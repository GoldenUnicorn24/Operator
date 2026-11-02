"""Offline contract tests; fixtures are synthetic, never substitute for the CI OSM extract."""
import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'tools' / (name + '.py'))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


extract = load('build_osm_roads')
packer = load('pack_geo')


def way(ident, points, tags, nodes=None):
    return {'type': 'way', 'id': ident, 'nodes': nodes or list(range(ident * 10, ident * 10 + len(points))),
            'geometry': [{'lon': x, 'lat': y} for x, y in points], 'tags': tags}


class ReadBinary:
    """Independent reader asserts exact field order used by Android's format contract."""
    def __init__(self, blob):
        self.blob = blob
        self.pos = 8

    def uint(self):
        value = shift = 0
        while True:
            byte = self.blob[self.pos]
            self.pos += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value
            shift += 7

    def signed(self):
        n = self.uint()
        return (n >> 1) ^ -(n & 1)

    def text(self):
        n = self.uint()
        data = self.blob[self.pos:self.pos + n].decode('utf-8')
        self.pos += n
        return data

    def points(self):
        n = self.uint()
        x = y = 0
        points = []
        for _ in range(n):
            x += self.signed()
            y += self.signed()
            points.extend([x / 100000, y / 100000])
        return points


class GeoPipelineTest(unittest.TestCase):
    def test_legacy_r1_remains_byte_identical(self):
        self.assertEqual(packer.pack({'roads': [[2, 'B', [0, 0, .00001, -.00001]]], 'places': []}),
                         b'EL110R1\0\x00\x01\x02\x01B\x02\x00\x00\x02\x01')

    def test_v2_preserves_name_ref_direction_and_area_holes(self):
        ring = [10, 53, 10.01, 53, 10.01, 53.01, 10, 53]
        data = {'version': 2, 'places': [], 'roads': [[3, 'K 3', [10, 53, 10.01, 53], 'Ölmühlenweg', -1]],
                'areas': [[0, [ring, ring]]]}
        blob = packer.pack(data)
        self.assertEqual(blob[:8], b'EL110R2\0')
        rd = ReadBinary(blob)
        self.assertEqual(rd.uint(), 0)  # places
        self.assertEqual(rd.uint(), 1)  # roads
        self.assertEqual(rd.uint(), 3)
        self.assertEqual(rd.text(), 'K 3')
        self.assertEqual(rd.text(), 'Ölmühlenweg')
        self.assertEqual(rd.uint(), 0)  # -1 -> 0
        self.assertEqual(rd.points(), [10, 53, 10.01, 53])
        self.assertEqual(rd.uint(), 1)
        self.assertEqual(rd.uint(), 0)
        self.assertEqual(rd.uint(), 2)
        self.assertEqual(rd.points(), ring)
        self.assertEqual(rd.points(), ring)
        self.assertEqual(rd.pos, len(blob))

    def test_all_three_directions_have_distinct_bytes(self):
        for direction in (-1, 0, 1):
            blob = packer.pack({'version': 2, 'roads': [[3, '', [0, 0, 1, 1], '', direction]]})
            rd = ReadBinary(blob)
            self.assertEqual(rd.uint(), 0)
            self.assertEqual(rd.uint(), 1)
            self.assertEqual(rd.uint(), 3)
            self.assertEqual(rd.text(), '')
            self.assertEqual(rd.text(), '')
            self.assertEqual(rd.uint(), direction + 1)

    def test_detail_network_filters_and_junction_splitting(self):
        points = [(10, 53), (10.001, 53), (10.002, 53)]
        elements = [way(1, points, {'highway': 'residential', 'ref': 'K 1', 'name': 'Wohnstraße', 'oneway': '-1'}, [1, 2, 3]),
                    way(2, [points[1], (10.001, 53.001)], {'highway': 'living_street'}, [2, 4]),
                    way(3, points[:2], {'highway': 'service', 'service': 'parking_aisle'}),
                    way(4, points[:2], {'highway': 'service', 'access': 'private'}),
                    way(5, points[:2], {'highway': 'service'}),
                    way(6, points[:2], {'highway': 'footway'}),
                    way(7, points[:2], {'highway': 'unclassified'})]
        data = extract.build('DE-HH', {'elements': elements})
        self.assertEqual(data['version'], 2)
        residential = [r for r in data['roads'] if r[0] == 3 and r[3] == 'Wohnstraße']
        self.assertEqual(len(residential), 2)
        self.assertTrue(all(r[1] == 'K 1' and r[4] == -1 for r in residential))
        self.assertEqual(residential[0][2][-2:], residential[1][2][:2])
        self.assertEqual(sum(r[0] == 4 for r in data['roads']), 1)
        self.assertEqual(sum(r[0] == 5 for r in data['roads']), 1)
        self.assertFalse(data['source']['buildings'])

    def test_oneway_overrides_and_roundabouts(self):
        self.assertEqual(extract.direction({'oneway': '-1'}), -1)
        self.assertEqual(extract.direction({'oneway': 'yes'}), 1)
        self.assertEqual(extract.direction({'junction': 'roundabout'}), 1)
        self.assertEqual(extract.direction({'junction': 'roundabout', 'oneway': 'no'}), 0)
        self.assertEqual(extract.direction({'highway': 'motorway'}), 1)
        self.assertEqual(extract.direction({'highway': 'residential'}), 0)

    def test_access_specific_motor_permission_has_precedence(self):
        self.assertTrue(extract.drivable({'highway': 'service', 'access': 'no', 'motor_vehicle': 'yes'}))
        self.assertFalse(extract.drivable({'highway': 'residential', 'motorcar': 'no'}))
        self.assertFalse(extract.drivable({'highway': 'residential', 'area': 'yes'}))

    def test_water_green_rail_and_multipolygon_hole(self):
        outer = [(10, 53), (10.02, 53), (10.02, 53.02), (10, 53)]
        hole = [(10.005, 53.005), (10.006, 53.005), (10.006, 53.006), (10.005, 53.005)]
        relation = {'type': 'relation', 'id': 100, 'tags': {'natural': 'water', 'type': 'multipolygon'},
                    'members': [dict(type='way', ref=1, geometry=way(1, outer[:3], {})['geometry']),
                                dict(type='way', ref=2, geometry=way(2, [outer[0], outer[2]], {})['geometry']),
                                dict(type='way', ref=3, geometry=way(3, hole, {})['geometry'])]}
        elements = [relation, way(1, outer, {'natural': 'water'}),
                    way(4, outer, {'leisure': 'park'}), way(5, outer, {'landuse': 'railway'}),
                    way(6, outer[:3], {'leisure': 'park'})]
        result = extract.build('DE-HH', {'elements': elements})
        self.assertEqual([a[0] for a in result['areas']], [0, 1, 2])
        self.assertEqual(len(result['areas'][0][1]), 2)
        self.assertTrue(all(r[:2] == r[-2:] for a in result['areas'] for r in a[1]))

    def test_missing_area_geometry_is_not_bridged(self):
        feature = way(1, [(10, 53), (10.01, 53), (10.01, 53.01), (10, 53)], {'natural': 'water'})
        feature['geometry'].insert(2, None)
        self.assertEqual(extract.area_rings(feature), [])

    def test_packer_rejects_unknown_version_direction_and_open_area(self):
        for data in ({'version': 3, 'roads': []},
                     {'version': 2, 'roads': [[3, '', [0, 0, 1, 1], '', 7]]},
                     {'version': 2, 'roads': [], 'areas': [[0, [[0, 0, 1, 0, 1, 1, 0, 1]]]]}):
            with self.assertRaises(ValueError):
                packer.pack(data)


if __name__ == '__main__':
    unittest.main()
