#!/usr/bin/env python3
"""Pack the per-state OSM road/town JSON (data/osm, ODbL) into the compact binary asset format.

Format (all integers unsigned LEB128 varints, signed values zigzag-encoded):
  magic  b"EL110R1\\0" (legacy) or b"EL110R2\\0" (version 2)
  places: count, then per place: name (len + UTF-8), kind (0 city, 1 town, 2 suburb, 3 other),
          population, lon*1e5, lat*1e5 (signed)
  roads:  count, then per road: class (byte), ref (len + UTF-8), point count,
          first lon/lat*1e5 (signed), then signed deltas
R2 roads additionally encode name (len + UTF-8) and direction byte (oneway + 1)
  after ref, before point count. After roads: areas count, per area kind byte
  (0 water, 1 park/green, 2 railway), ring count, per ring point count + deltas.
The JSON sources stay in the repository as the open copy of the ODbL-derived database.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "data/osm"
OUT = ROOT / "app/src/main/native_assets/geo"
KINDS = {"city": 0, "town": 1, "suburb": 2}


def varint(n: int, out: bytearray) -> None:
    assert n >= 0
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return


def zz(n: int) -> int:
    return (n << 1) ^ (n >> 63)


def text(s: str, out: bytearray) -> None:
    b = s.encode("utf-8")
    varint(len(b), out)
    out += b


def pack(data: dict) -> bytes:
    version = data.get("version", 1)
    if version not in (1, 2):
        raise ValueError(f"Unsupported map format {version}")
    out = bytearray(b"EL110R2\0" if version == 2 else b"EL110R1\0")
    places = data.get("places", [])
    varint(len(places), out)
    for name, kind, pop, lon, lat in places:
        text(name, out)
        out.append(KINDS.get(kind, 3))
        varint(max(0, int(pop)), out)
        varint(zz(round(lon * 1e5)), out)
        varint(zz(round(lat * 1e5)), out)
    roads = data["roads"]
    varint(len(roads), out)
    for row in roads:
        cls, ref, flat = row[:3]
        out.append(int(cls) & 0xFF)
        text(ref or "", out)
        if version == 2:
            name, oneway = row[3:5]
            if oneway not in (-1, 0, 1):
                raise ValueError("Invalid oneway")
            text(name or "", out)
            out.append(oneway + 1)
        if len(flat) < 4 or len(flat) % 2:
            raise ValueError("Invalid road geometry")
        n = len(flat) // 2
        varint(n, out)
        px = py = 0
        for i in range(n):
            x = round(flat[2 * i] * 1e5)
            y = round(flat[2 * i + 1] * 1e5)
            varint(zz(x - px), out)
            varint(zz(y - py), out)
            px, py = x, y
    if version == 2:
        areas = data.get("areas", [])
        varint(len(areas), out)
        for kind, rings in areas:
            if kind not in (0, 1, 2):
                raise ValueError("Invalid area kind")
            out.append(kind)
            varint(len(rings), out)
            for flat in rings:
                if len(flat) < 8 or len(flat) % 2 or flat[:2] != flat[-2:]:
                    raise ValueError("Invalid area ring")
                varint(len(flat) // 2, out)
                px = py = 0
                for x, y in zip(flat[::2], flat[1::2]):
                    x, y = round(x * 1e5), round(y * 1e5)
                    varint(zz(x - px), out)
                    varint(zz(y - py), out)
                    px, py = x, y
    return bytes(out)


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    total_in = total_out = 0
    for src in sorted(SRC.glob("roads-*.json")):
        data = json.loads(src.read_text())
        blob = pack(data)
        dst = OUT / (src.stem + ".bin")
        dst.write_bytes(blob)
        total_in += src.stat().st_size
        total_out += len(blob)
        print(f"{src.name}: {src.stat().st_size} -> {len(blob)} bytes")
    print(f"total {total_in} -> {total_out} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
