#!/usr/bin/env python3
"""Rebuild compact offline BKG/geoBoundaries geometry; never contacts map tile servers.

Input is pinned to the source commit below. No network is needed for normal builds.
Hamburg roads (optional input) are an explicit small Overpass extract, not tiles.
"""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
COMMIT = "f6920d43375832a91201bdb7954a200beaa40202"
URL = f"https://media.githubusercontent.com/media/wmgeolab/geoBoundaries/{COMMIT}/releaseData/gbOpen/DEU/ADM1/geoBoundaries-DEU-ADM1_simplified.geojson"


def rounded(value):
    if isinstance(value, list):
        return [rounded(x) for x in value]
    return round(value, 5)


def simplify_line(points, tolerance=0.000035):
    """Douglas–Peucker (~3 m at Hamburg); endpoints and junctions survive."""
    if len(points) <= 2:
        return points
    a, b = points[0], points[-1]
    dx, dy = b[0] - a[0], b[1] - a[1]
    length = dx * dx + dy * dy
    distances = []
    for i, p in enumerate(points[1:-1], 1):
        t = max(0, min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / length)) if length else 0
        distances.append(((p[0] - a[0] - t * dx) ** 2 + (p[1] - a[1] - t * dy) ** 2, i))
    distance, index = max(distances)
    if distance <= tolerance * tolerance:
        return [a, b]
    return simplify_line(points[:index + 1], tolerance)[:-1] + simplify_line(points[index:], tolerance)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path)
    parser.add_argument("--hamburg-roads", type=Path)
    args = parser.parse_args()
    raw = args.input.read_bytes() if args.input else urlopen(URL).read()
    source = json.loads(raw)
    features = []
    for feature in source["features"]:
        properties = feature["properties"]
        geometry = feature["geometry"]
        features.append({"id": properties["shapeISO"].removeprefix("DE-"),
                         "name": properties["shapeName"],
                         "boundary": {"type": geometry["type"], "coordinates": rounded(geometry["coordinates"])}})
    output = ROOT / "web/data/geo"
    output.mkdir(parents=True, exist_ok=True)
    (output / "germany-states.json").write_text(json.dumps(features, ensure_ascii=False, separators=(",", ":")) + "\n")
    metadata = {"source": URL, "sourceSHA256": hashlib.sha256(raw).hexdigest(),
                "attribution": "© GeoBasis-DE / BKG 2021; geoBoundaries (William & Mary)",
                "license": "Datenlizenz Deutschland – Namensnennung – Version 2.0",
                "licenseURL": "https://www.govdata.de/dl-de/by-2-0",
                "modifications": "Properties reduced; WGS84 coordinates rounded to five decimal places (~1 m). Upstream simplified geometry retained, including islands and exclaves."}
    (output / "source.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
    if args.hamburg_roads:
        data = json.loads(args.hamburg_roads.read_text())
        roads = [{"name": e["properties"].get("strassenname", "Hauptverkehrsstraße"),
                  "class": e["properties"].get("klasse", "HVS"),
                  "points": rounded(simplify_line(e["geometry"]["coordinates"]))}
                 for e in data["features"] if e["geometry"]["type"] == "LineString" and len(e["geometry"]["coordinates"]) >= 2]
        (output / "hamburg-roads.json").write_text(json.dumps(roads, ensure_ascii=False, separators=(",", ":")) + "\n")
        metadata = {"source": "https://geodienste.hamburg.de/HH_WFS_Hauptverkehrsstrassen?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature&TYPENAMES=app:hauptverkehrsstrassen&SRSNAME=EPSG:4326&OUTPUTFORMAT=application/geo%2Bjson",
                    "sourceSHA256": hashlib.sha256(args.hamburg_roads.read_bytes()).hexdigest(),
                    "attribution": "Freie und Hansestadt Hamburg, Behörde für Wirtschaft, Verkehr und Innovation",
                    "license": "Datenlizenz Deutschland Namensnennung 2.0",
                    "licenseURL": "https://www.govdata.de/dl-de/by-2-0",
                    "features": len(roads), "retrieved": "2026-11-01",
                    "modifications": "LineString properties reduced to name/class; Douglas-Peucker 0.000035 degree tolerance; WGS84 coordinates rounded to five decimals."}
        (output / "hamburg-roads-source.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
