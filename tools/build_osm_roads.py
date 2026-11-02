#!/usr/bin/env python3
"""One-off extraction of the main road network and towns for each German state.

Data © OpenStreetMap contributors, ODbL 1.0 (https://www.openstreetmap.org/copyright).
Queried once per state from the public Overpass API in CI; the derived, simplified asset is
committed to the repository so the app and later builds never contact OSM services again.
No tile servers are used. The derived road database stays available under ODbL (see
THIRD_PARTY_NOTICES.md).

Output per state: data/osm/roads-<CODE>.json
  {"roads": [[class, ref, [lon,lat, ...]], ...],   class 0 motorway, 1 trunk/primary, 2 secondary/tertiary
   "places": [[name, kind, population, lon, lat], ...]}
Polylines are split at junctions; junction coordinates are written identically (5 decimals) so
the app can build the routing topology by exact coordinate match.
"""
from __future__ import annotations

import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data/osm"  # open ODbL copy; tools/pack_geo.py writes the APK assets
CACHE = ROOT / "build/osm-cache"
ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]
STATES = ["DE-BW", "DE-BY", "DE-BE", "DE-BB", "DE-HB", "DE-HH", "DE-HE", "DE-MV",
          "DE-NI", "DE-NW", "DE-RP", "DE-SL", "DE-SN", "DE-ST", "DE-SH", "DE-TH"]
CITY_STATES = {"DE-HH", "DE-HB", "DE-BE"}
SMALL = {"DE-SL"}
CLASS = {"motorway": 0, "motorway_link": 0, "trunk": 1, "trunk_link": 1, "primary": 1,
         "primary_link": 1, "secondary": 2, "secondary_link": 2, "tertiary": 2,
         "tertiary_link": 2, "residential": 3, "unclassified": 3, "service": 4, "living_street": 5}


def query(code: str) -> dict:
    CACHE.mkdir(parents=True, exist_ok=True)
    cached = CACHE / f"{code}{'-detail-v2' if code == 'DE-HH' else ''}.json"
    if cached.exists():
        return json.loads(cached.read_text())
    if code == "DE-HH":
        hw = "|".join(CLASS)
    elif code in CITY_STATES:
        hw = "motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary"
    elif code in SMALL:
        hw = "motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary"
    else:
        hw = "motorway|motorway_link|trunk|trunk_link|primary|primary_link"
    areas = """
way(area.a)["natural"="water"];
way(area.a)["waterway"="riverbank"];
way(area.a)["landuse"~"^(forest|grass|meadow|recreation_ground|railway)$"];
way(area.a)["leisure"~"^(park|garden|nature_reserve)$"];
relation(area.a)["type"="multipolygon"]["natural"="water"];
relation(area.a)["type"="multipolygon"]["landuse"~"^(forest|grass|meadow|recreation_ground|railway)$"];
relation(area.a)["type"="multipolygon"]["leisure"~"^(park|garden|nature_reserve)$"];
""" if code == "DE-HH" else ""
    # Small declared limits: busy Overpass servers admit modest queries first.
    q = f"""[out:json][timeout:240][maxsize:402653184];
area["ISO3166-2"="{code}"]["admin_level"="4"]->.a;
(way(area.a)["highway"~"^({hw})$"];{areas});
out body geom qt;
node(area.a)["place"~"^(city|town{'|suburb' if code in CITY_STATES else ''})$"];
out body qt;"""
    body = urllib.parse.urlencode({"data": q}).encode()
    last = None
    for attempt in range(5):
        url = ENDPOINTS[attempt % len(ENDPOINTS)]
        try:
            req = urllib.request.Request(url, data=body, headers={
                "User-Agent": "Einsatzleitstelle110-build/1.0 (one-off offline extract; github.com/GoldenUnicorn24/Operator)"})
            with urllib.request.urlopen(req, timeout=1200) as r:
                raw = r.read()
            data = json.loads(raw)
            if not data.get("elements"):
                raise RuntimeError("empty response")
            if data.get("remark"):
                raise RuntimeError("incomplete Overpass response: " + data["remark"])
            cached.write_bytes(raw)
            return data
        except Exception as e:  # noqa: BLE001
            last = e
            print(f"  {code}: attempt {attempt + 1} via {url} failed: {e}", file=sys.stderr)
            time.sleep(30 + attempt * 30)
    raise RuntimeError(f"{code}: {last}")


def perp(p, a, b) -> float:
    (x, y), (x1, y1), (x2, y2) = p, a, b
    dx, dy = x2 - x1, y2 - y1
    if dx == 0 and dy == 0:
        return ((x - x1) ** 2 + (y - y1) ** 2) ** 0.5
    t = max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)))
    return ((x - x1 - t * dx) ** 2 + (y - y1 - t * dy) ** 2) ** 0.5


def simplify(points, tol):
    if len(points) <= 2:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        s, e = stack.pop()
        best, idx = 0.0, -1
        for i in range(s + 1, e):
            d = perp(points[i], points[s], points[e])
            if d > best:
                best, idx = d, i
        if best > tol and idx > 0:
            keep[idx] = True
            stack += [(s, idx), (idx, e)]
    return [p for p, k in zip(points, keep) if k]


def drivable(tags: dict) -> bool:
    """Ordinary motor traffic only; no driveways into private/parking/loading interiors."""
    if tags.get("highway") not in CLASS or tags.get("area") == "yes":
        return False
    access = tags.get("motorcar", tags.get("motor_vehicle", tags.get("vehicle", tags.get("access", "yes"))))
    if access in {"no", "private", "agricultural", "forestry"}:
        return False
    if tags.get("highway") == "service" and tags.get("service") in {"parking_aisle", "driveway", "emergency_access"}:
        return False
    return True


def direction(tags: dict) -> int:
    value = tags.get("oneway", "").lower()
    if value in {"-1", "reverse"}:
        return -1
    if value in {"yes", "true", "1"}:
        return 1
    if value in {"no", "false", "0"}:
        return 0
    return 1 if tags.get("junction") == "roundabout" or tags.get("highway") in {"motorway", "motorway_link"} else 0


def area_kind(tags: dict):
    if tags.get("natural") == "water" or tags.get("waterway") == "riverbank":
        return 0
    if tags.get("landuse") == "railway":
        return 2
    if tags.get("landuse") in {"forest", "grass", "meadow", "recreation_ground"} or tags.get("leisure") in {"park", "garden", "nature_reserve"}:
        return 1
    return None


def area_rings(element: dict) -> list:
    """Join relation member geometry by endpoints, preserving separate inner rings.

    Only complete closed rings are retained. The Android renderer uses even/odd fill;
    open ways are never closed across missing data. No synthetic water/park polygons.
    """
    if element["type"] == "way":
        segments = [element.get("geometry", [])]
    else:
        segments = [m.get("geometry", []) for m in element.get("members", []) if m.get("type") == "way"]
    segments = [[(round(p["lon"], 5), round(p["lat"], 5)) for p in g if p is not None] for g in segments]
    segments = [g for g in segments if len(g) >= 2]
    rings = []
    while segments:
        ring = segments.pop()
        while ring[0] != ring[-1]:
            match = next((i for i, seg in enumerate(segments) if ring[-1] in (seg[0], seg[-1])), None)
            if match is None:
                break
            seg = segments.pop(match)
            if seg[-1] == ring[-1]:
                seg.reverse()
            ring.extend(seg[1:])
        if len(ring) >= 4 and ring[0] == ring[-1]:
            reduced = simplify(ring, .000025)
            if len(reduced) >= 4:
                rings.append([v for p in reduced for v in p])
    return rings


def build(code: str, data: dict | None = None) -> dict:
    data = query(code) if data is None else data
    ways = [e for e in data["elements"] if e["type"] == "way" and "geometry" in e and drivable(e.get("tags", {}))]
    nodes = [e for e in data["elements"] if e["type"] == "node"]
    use = {}
    for w in ways:
        for n in w["nodes"]:
            use[n] = use.get(n, 0) + 1
    tol = .00002 if code == "DE-HH" else 0.00012 if code in CITY_STATES else 0.0004  # ~10 m / ~35 m
    roads = []
    for w in ways:
        tags = w.get("tags", {})
        cls = CLASS.get(tags.get("highway", ""), 2)
        ref = (tags.get("ref") or (tags.get("name") if code != "DE-HH" else "") or "").split(";")[0].strip()[:24]
        name = (tags.get("name") or "").strip()[:120]
        ids, geom = w["nodes"], w["geometry"]
        if len(ids) != len(geom) or len(ids) < 2:
            continue
        start = 0
        for i in range(1, len(ids)):
            if i == len(ids) - 1 or use.get(ids[i], 0) > 1:
                pts = [(geom[k]["lon"], geom[k]["lat"]) for k in range(start, i + 1)]
                pts = simplify(pts, tol)
                flat = []
                for x, y in pts:
                    flat += [round(x, 5), round(y, 5)]
                if len(flat) >= 4:
                    roads.append([cls, ref, flat, name, direction(tags)] if code == "DE-HH" else [cls, ref, flat])
                start = i
    places = []
    for n in nodes:
        tags = n.get("tags", {})
        name = tags.get("name")
        if not name:
            continue
        try:
            pop = int(str(tags.get("population", "0")).replace(".", "").replace(" ", "").split(";")[0])
        except ValueError:
            pop = 0
        kind = tags.get("place", "town")
        if pop == 0:
            pop = {"city": 100000, "town": 12000, "suburb": 20000}.get(kind, 5000)
        places.append([name[:40], kind, pop, round(n["lon"], 5), round(n["lat"], 5)])
    places.sort(key=lambda p: -p[2])
    result = {"roads": roads, "places": places}
    if code == "DE-HH":
        # Avoid duplicate relation members when a closed tagged way is also a member.
        relations = [e for e in data["elements"] if e["type"] == "relation" and area_kind(e.get("tags", {})) is not None]
        members = {m["ref"] for e in relations for m in e.get("members", []) if m.get("type") == "way"}
        areas = []
        for e in data["elements"]:
            if e["type"] not in {"way", "relation"} or (e["type"] == "way" and e["id"] in members):
                continue
            kind = area_kind(e.get("tags", {}))
            rings = area_rings(e) if kind is not None else []
            if rings:
                areas.append([kind, rings])
        result.update(version=2, areas=areas, source={
            "copyright": "© OpenStreetMap contributors", "license": "ODbL-1.0",
            "url": "https://www.openstreetmap.org/copyright",
            "extraction": "CI one-off Hamburg drivable detail network; no turn restrictions",
            "buildings": False,
        })
    return result


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    only = [a for a in sys.argv[1:] if not a.startswith("--")] or STATES
    summary_path = OUT / "roads.summary.json"
    summary = json.loads(summary_path.read_text()) if summary_path.exists() else {}
    missing = []
    started = time.time()
    for i, code in enumerate(only):
        path = OUT / f"roads-{code}.json"
        if time.time() - started > 85 * 60 and not path.exists():
            print(code, "time budget used up – left for the next run", flush=True)
            missing.append(code)
            continue
        if path.exists() and "--force" not in sys.argv and not (code == "DE-HH" and json.loads(path.read_text()).get("version", 1) < 2):
            print(code, "already extracted, skipping", flush=True)
            continue
        try:
            result = build(code)
        except Exception as e:  # noqa: BLE001
            print(f"{code}: extraction failed ({e}); will retry in a later run", file=sys.stderr, flush=True)
            missing.append(code)
            continue
        if len(result["roads"]) < 50:
            print(f"{code}: too few roads ({len(result['roads'])})", file=sys.stderr)
            missing.append(code)
            continue
        path.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
        summary[code] = {
            "roads": len(result["roads"]),
            "vertices": sum(len(r[2]) // 2 for r in result["roads"]),
            "places": len(result["places"]),
            "bytes": path.stat().st_size,
            "version": result.get("version", 1),
            "areas": len(result.get("areas", [])),
            "named": sum(len(r) > 3 and bool(r[3]) for r in result["roads"]),
            "oneway": sum(len(r) > 4 and r[4] != 0 for r in result["roads"]),
        }
        print(code, summary[code], flush=True)
        if i < len(only) - 1:
            time.sleep(8)  # be polite to the shared Overpass service
        summary_path.write_text(json.dumps(summary, indent=1))
    print("missing:", missing, flush=True)
    # Preserve extracted progress, but failed extraction must not look successful.
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
