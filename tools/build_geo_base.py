#!/usr/bin/env python3
"""Build the offline topographic base map for all 16 German states.

Source: Natural Earth 1:10m vector data (public domain, https://www.naturalearthdata.com).
Runs in CI (internet required once); the generated asset is committed and the APK stays
fully offline. No OSM tile servers are touched.

Output: app/src/main/native_assets/geo/germany-base.json
  roads:  [[class, ref, [lon,lat,lon,lat,...]], ...]   class 0 motorway, 1 major, 2 secondary, 9 connector
  rivers: [[weight, [..]], ...]
  lakes:  [[[..ring..], ...], ...]
  urban:  [[[..ring..], ...], ...]
  places: [[name, state, pop, rank, lon, lat], ...]
"""
from __future__ import annotations

import io
import json
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

import shapefile  # pyshp
from shapely.geometry import LineString, MultiLineString, Point, shape
from shapely.ops import unary_union
from shapely.strtree import STRtree

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/native_assets"
OUT = ASSETS / "geo/germany-base.json"
CACHE = ROOT / "build/ne-cache"

CDN = "https://naciscdn.org/naturalearth/10m/{kind}/{name}.zip"
GH = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/10m_{kind}/{name}.{ext}"
LAYERS = {
    "roads": ("cultural", "ne_10m_roads"),
    "rivers": ("physical", "ne_10m_rivers_lake_centerlines"),
    "rivers_eu": ("physical", "ne_10m_rivers_europe"),
    "lakes": ("physical", "ne_10m_lakes"),
    "lakes_eu": ("physical", "ne_10m_lakes_europe"),
    "urban": ("cultural", "ne_10m_urban_areas"),
    "places": ("cultural", "ne_10m_populated_places"),
}


def fetch(url: str) -> bytes:
    last = None
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Einsatzleitstelle110-geo-build/1.0"})
            with urllib.request.urlopen(req, timeout=180) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(3 + attempt * 4)
    raise RuntimeError(f"download failed {url}: {last}")


def read_layer(key: str):
    kind, name = LAYERS[key]
    CACHE.mkdir(parents=True, exist_ok=True)
    folder = CACHE / name
    if not (folder / f"{name}.shp").exists():
        folder.mkdir(exist_ok=True)
        try:
            data = fetch(CDN.format(kind=kind, name=name))
            zipfile.ZipFile(io.BytesIO(data)).extractall(folder)
        except Exception as e:  # noqa: BLE001
            print(f"CDN failed for {name} ({e}); trying GitHub mirror", file=sys.stderr)
            for ext in ("shp", "shx", "dbf", "prj"):
                try:
                    (folder / f"{name}.{ext}").write_bytes(fetch(GH.format(kind=kind, name=name, ext=ext)))
                except Exception:  # noqa: BLE001
                    if ext != "prj":
                        raise
    shp = next(folder.rglob(f"{name}.shp"))
    reader = shapefile.Reader(str(shp), encoding="latin1")
    fields = [f[0].lower() for f in reader.fields[1:]]
    for sr in reader.iterShapeRecords():
        if sr.shape.shapeType == shapefile.NULL:
            continue
        yield dict(zip(fields, sr.record)), shape(sr.shape.__geo_interface__)


def r4(v: float) -> float:
    return round(v, 4)


def flat(coords) -> list[float]:
    out: list[float] = []
    for x, y in coords:
        out += [r4(x), r4(y)]
    return out


def lines_of(geom):
    if geom.is_empty:
        return []
    if isinstance(geom, LineString):
        return [geom]
    if isinstance(geom, MultiLineString):
        return list(geom.geoms)
    if hasattr(geom, "geoms"):
        return [g for sub in geom.geoms for g in lines_of(sub)]
    return []


def polys_of(geom):
    if geom.is_empty:
        return []
    if geom.geom_type == "Polygon":
        return [geom]
    if hasattr(geom, "geoms"):
        return [g for sub in geom.geoms for g in polys_of(sub)]
    return []


def poly_rings(poly, tol):
    p = poly.simplify(tol, preserve_topology=True)
    if p.is_empty or p.area < tol * tol * 4:
        return None
    rings = [flat(p.exterior.coords)] + [flat(i.coords) for i in p.interiors if len(i.coords) >= 4]
    return rings if len(rings[0]) >= 8 else None


def main() -> int:
    states = json.loads((ASSETS / "states.geojson").read_text())
    state_shapes = []
    for f in states["features"]:
        p = f["properties"]
        state_shapes.append((p.get("code", p.get("id")), shape(f["geometry"]).buffer(0)))
    germany = unary_union([g for _, g in state_shapes])
    clip = germany.buffer(0.25)
    minx, miny, maxx, maxy = clip.bounds
    print("Germany bounds", germany.bounds)

    # Roads -----------------------------------------------------------------------------
    roads = []
    road_lines = []
    for props, geom in read_layer("roads"):
        if not geom.intersects(clip):
            continue
        typ = str(props.get("type", ""))
        if "Ferry" in typ or typ in ("Track", "Unknown"):
            continue
        expressway = int(props.get("expressway") or 0) == 1
        cls = 0 if expressway else (1 if typ in ("Major Highway", "Beltway") else 2)
        ref = str(props.get("label") or props.get("name") or "").strip()[:24]
        for line in lines_of(geom.intersection(clip)):
            line = line.simplify(0.0012, preserve_topology=False)
            if len(line.coords) >= 2 and line.length > 0:
                roads.append([cls, ref, flat(line.coords)])
                road_lines.append(line)
    print("road segments", len(roads))

    # Connectivity: snap at 3 decimals (~100 m), link small components to the main network.
    def key(x, y):
        return (round(x, 3), round(y, 3))

    parent: dict = {}

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    for _, _, coords in roads:
        prev = None
        for i in range(0, len(coords), 2):
            k = key(coords[i], coords[i + 1])
            parent.setdefault(k, k)
            if prev is not None:
                union(prev, k)
            prev = k
    comps: dict = {}
    for k in parent:
        comps.setdefault(find(k), []).append(k)
    ordered = sorted(comps.values(), key=len, reverse=True)
    main_nodes = ordered[0] if ordered else []
    print("components", len(ordered), "main", len(main_nodes))
    connectors = 0
    if main_nodes:
        main_tree = STRtree([Point(k) for k in main_nodes])
        for comp in ordered[1:]:
            best = None
            for k in comp[:: max(1, len(comp) // 60)]:
                idx = main_tree.nearest(Point(k))
                m = main_nodes[int(idx)]
                d = (m[0] - k[0]) ** 2 + (m[1] - k[1]) ** 2
                if best is None or d < best[0]:
                    best = (d, k, m)
            if best and best[0] ** 0.5 < 0.12:  # ~10 km
                roads.append([9, "", [best[1][0], best[1][1], best[2][0], best[2][1]]])
                connectors += 1
    print("connectors", connectors)

    # Water -----------------------------------------------------------------------------
    rivers = []
    for key_name in ("rivers", "rivers_eu"):
        for props, geom in read_layer(key_name):
            if not geom.intersects(clip):
                continue
            w = float(props.get("strokeweig") or 0.6)
            for line in lines_of(geom.intersection(clip)):
                line = line.simplify(0.002, preserve_topology=False)
                if len(line.coords) >= 2:
                    rivers.append([round(min(3.0, max(0.4, w)), 2), flat(line.coords)])
    lakes = []
    for key_name in ("lakes", "lakes_eu"):
        for _, geom in read_layer(key_name):
            if not geom.intersects(clip):
                continue
            for poly in polys_of(geom.intersection(clip)):
                rings = poly_rings(poly, 0.0015)
                if rings:
                    lakes.append(rings)
    urban = []
    for _, geom in read_layer("urban"):
        if not geom.intersects(clip):
            continue
        for poly in polys_of(geom.intersection(clip)):
            rings = poly_rings(poly, 0.0015)
            if rings:
                urban.append(rings)
    print("rivers", len(rivers), "lakes", len(lakes), "urban", len(urban))

    # Places ----------------------------------------------------------------------------
    places = []
    for props, geom in read_layer("places"):
        if str(props.get("adm0_a3", "")) != "DEU":
            continue
        pt = geom if geom.geom_type == "Point" else geom.centroid
        code = next((c for c, g in state_shapes if g.contains(pt)), None)
        if code is None:
            code = min(state_shapes, key=lambda s: s[1].distance(pt))[0]
        name = str(props.get("name") or props.get("nameascii") or "").strip()
        pop = int(float(props.get("pop_max") or 0))
        rank = int(float(props.get("scalerank") or 10))
        if name:
            places.append([name, code, pop, rank, r4(pt.x), r4(pt.y)])
    places.sort(key=lambda p: -p[2])
    print("places", len(places))
    per_state: dict = {}
    for p in places:
        per_state[p[1]] = per_state.get(p[1], 0) + 1
    print("places per state", dict(sorted(per_state.items())))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    data = {
        "source": "Natural Earth 1:10m (public domain) · generated by tools/build_geo_base.py",
        "bounds": [r4(minx), r4(miny), r4(maxx), r4(maxy)],
        "roads": roads,
        "rivers": rivers,
        "lakes": lakes,
        "urban": urban,
        "places": places,
    }
    OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    summary = {k: len(v) for k, v in data.items() if isinstance(v, list)}
    summary["bytes"] = OUT.stat().st_size
    summary["road_classes"] = {c: sum(1 for r in roads if r[0] == c) for c in (0, 1, 2, 9)}
    summary["places_per_state"] = per_state
    (OUT.parent / "germany-base.summary.json").write_text(json.dumps(summary, indent=1, ensure_ascii=False))
    print(json.dumps(summary, ensure_ascii=False))
    if len(roads) < 500 or len(places) < 50:
        print("Too little geometry – refusing to write a degraded map", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
