#!/usr/bin/env python3
"""Pack the per-state OSM road/town JSON (data/osm, ODbL) into the compact binary asset format.

Format (all integers unsigned LEB128 varints, signed values zigzag-encoded):
  magic  b"EL110R1\\0"
  places: count, then per place: name (len + UTF-8), kind (0 city, 1 town, 2 suburb, 3 other),
          population, lon*1e5, lat*1e5 (signed)
  roads:  count, then per road: class (byte), ref (len + UTF-8), point count,
          first lon/lat*1e5 (signed), then signed deltas
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
    out = bytearray(b"EL110R1\0")
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
    for cls, ref, flat in roads:
        out.append(int(cls) & 0xFF)
        text(ref or "", out)
        n = len(flat) // 2
        varint(n, out)
        px = py = 0
        for i in range(n):
            x = round(flat[2 * i] * 1e5)
            y = round(flat[2 * i + 1] * 1e5)
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
