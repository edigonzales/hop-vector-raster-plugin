#!/usr/bin/env python3
"""Validate the cloud-optimized layout of a generated GeoTIFF.

The structural checks use only the standard library so they run on every CI
platform. With --gdal the independent GDAL validator is consulted as well
(full check including block leaders/trailers).
"""
from __future__ import annotations

import argparse
import struct
import sys
from pathlib import Path

TYPE_SIZES = {1: 1, 2: 1, 3: 2, 4: 4, 5: 8, 6: 1, 7: 1, 8: 2, 9: 4, 10: 8, 11: 4, 12: 8, 13: 4, 16: 8, 17: 8, 18: 8}
GHOST_PREFIX = "GDAL_STRUCTURAL_METADATA_SIZE="


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        if data[:2] == b"II":
            self.order = "<"
        elif data[:2] == b"MM":
            self.order = ">"
        else:
            raise AssertionError("Not a TIFF file")
        self.big = struct.unpack(self.order + "H", data[2:4])[0] == 43
        self.header = 16 if self.big else 8
        self.inline = 8 if self.big else 4
        self.entry = 20 if self.big else 12
        self.count_format = "Q" if self.big else "H"
        self.offset_format = "Q" if self.big else "I"

    def uint(self, offset: int, size: int) -> int:
        raw = self.data[offset : offset + size]
        if len(raw) != size:
            raise AssertionError("Truncated TIFF")
        return struct.unpack(self.order + {2: "H", 4: "I", 8: "Q"}[size], raw)[0]

    def first_ifd(self) -> int:
        return self.uint(self.header - (8 if self.big else 4), 8 if self.big else 4)

    def fields(self, offset: int) -> tuple[dict[int, list[int]], int]:
        count = self.uint(offset, 2 if not self.big else 8)
        base = offset + (2 if not self.big else 8)
        values: dict[int, list[int]] = {}
        for index in range(count):
            entry = base + index * self.entry
            tag = self.uint(entry, 2)
            type_code = self.uint(entry + 2, 2)
            field_count = self.uint(entry + 4, 4 if not self.big else 8)
            if type_code not in TYPE_SIZES:
                raise AssertionError(f"Unsupported TIFF type {type_code}")
            size = TYPE_SIZES[type_code] * field_count
            start = entry + (8 if not self.big else 12)
            if size > self.inline:
                start = self.uint(start, 4 if not self.big else 8)
            raw = self.data[start : start + size]
            if len(raw) != size:
                raise AssertionError("Truncated TIFF value")
            if type_code in (3, 4):
                values[tag] = list(struct.unpack(self.order + f"{field_count}{'H' if type_code == 3 else 'I'}", raw))
            elif type_code in (16, 17, 18):
                values[tag] = list(struct.unpack(self.order + f"{field_count}Q", raw))
            else:
                values[tag] = [raw]
        next_offset = self.uint(base + count * self.entry, 4 if not self.big else 8)
        return values, next_offset

    def ghost_size(self) -> int | None:
        prefix = self.data[self.header : self.header + len(GHOST_PREFIX)]
        if prefix.decode("latin1", "replace") != GHOST_PREFIX:
            return None
        raw = self.data[self.header : self.header + 43].decode("latin1", "replace")
        return int(raw[len(GHOST_PREFIX) :][0:6])


def validate(path: Path) -> None:
    data = path.read_bytes()
    reader = Reader(data)
    if path.with_suffix(path.suffix + ".ovr").exists():
        raise AssertionError("Overviews must be internal, not in an .ovr sidecar")
    ghost = reader.ghost_size()
    expected = reader.header
    if ghost is not None:
        expected = reader.header + 43 + ghost
        expected += expected % 2
    if reader.first_ifd() != expected:
        raise AssertionError(f"Main IFD must start at {expected}, not {reader.first_ifd()}")
    ifds = []
    offsets = []
    offset = reader.first_ifd()
    while offset:
        values, next_offset = reader.fields(offset)
        ifds.append(values)
        offsets.append(offset)
        offset = next_offset
    for tag in (256, 257, 258, 259, 262, 277, 322, 323, 324, 325):
        if tag not in ifds[0]:
            raise AssertionError(f"Main IFD is missing tag {tag}")
    if any(b <= a for a, b in zip(offsets, offsets[1:])):
        raise AssertionError("IFD offsets must increase from main to smallest overview")
    for index in range(1, len(ifds)):
        values = ifds[index]
        width, height = values[256][0], values[257][0]
        tile_width, tile_height = values[322][0], values[323][0]
        if tile_width != tile_height:
            raise AssertionError("Tiles must be square")
        if tile_width > 1024 or tile_width % 16 != 0:
            raise AssertionError(f"Invalid tile size {tile_width}")
        if width > ifds[index - 1][256][0] or height > ifds[index - 1][257][0]:
            raise AssertionError("Overview dimensions must decrease")
        if 254 not in values or not (values[254][0] & 1):
            raise AssertionError(f"Overview {index} lacks the reduced-resolution flag")
        for tag in (33550, 33922, 34264, 34735, 34736, 34737):
            if tag in values:
                raise AssertionError(f"Overview {index} must not carry georeferencing (tag {tag})")
    if 34735 not in ifds[0]:
        raise AssertionError("Main image must carry the GeoKey directory")
    tile_offsets = [values[324] for values in ifds]
    tile_counts = [values[325] for values in ifds]
    smallest = ifds[-1]
    across = -(-smallest[256][0] // smallest[322][0])
    down = -(-smallest[257][0] // smallest[323][0])
    if across > 1 and down > 1:
        raise AssertionError("Smallest overview must span at most one tile in one direction")
    # Overview data first, smallest overview first, main image last.
    previous = None
    for index in range(len(tile_offsets) - 1, 0, -1):
        first = min(tile_offsets[index])
        if first < offsets[index]:
            raise AssertionError(f"Overview {index} data starts before its IFD")
        if previous is not None and first < previous:
            raise AssertionError("Overview data must be written from smallest to largest")
        previous = first
    if previous is not None and min(tile_offsets[0]) < previous:
        raise AssertionError("Main image data must be written after the overviews")
    # Row-major blocks with GDAL-compatible leaders and trailers.
    ghost_text = b""
    if ghost is not None:
        ghost_text = data[reader.header + 43 : reader.header + 43 + ghost]
    leaders = b"BLOCK_LEADER=SIZE_AS_UINT4" in ghost_text
    trailers = b"BLOCK_TRAILER=LAST_4_BYTES_REPEATED" in ghost_text
    for tile_list, count_list in zip(tile_offsets, tile_counts):
        if sorted(tile_list) != tile_list:
            raise AssertionError("Tiles must be stored in row-major order")
        for tile, count in zip(tile_list, count_list):
            if leaders:
                leader = struct.unpack("<I", data[tile - 4 : tile])[0]
                if leader != count:
                    raise AssertionError(f"Block leader {leader} does not match {count}")
            if trailers and count >= 4:
                if data[tile + count - 4 : tile + count] != data[tile + count : tile + count + 4]:
                    raise AssertionError("Block trailer does not repeat the last bytes")


def validate_with_gdal(path: Path) -> None:
    from osgeo import gdal  # type: ignore
    try:
        from osgeo_utils.samples.validate_cloud_optimized_geotiff import validate as gdal_validate  # type: ignore
    except ImportError as error:
        raise SystemExit(f"GDAL validator is unavailable: {error}")

    dataset = gdal.Open(str(path))
    if dataset is None:
        raise SystemExit(f"GDAL cannot open {path}")
    errors, details = gdal_validate(dataset, check_tiled=True, full_check=True)
    if errors:
        raise SystemExit("GDAL COG validation errors:\n" + "\n".join(errors))
    overviews = dataset.GetRasterBand(1).GetOverviewCount()
    print(f"GDAL validation OK; overviews={overviews}; ifd_offsets={details['ifd_offsets']}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path)
    parser.add_argument("--gdal", action="store_true", help="also run the GDAL validator")
    args = parser.parse_args()
    try:
        validate(args.file)
    except AssertionError as error:
        raise SystemExit(f"COG structure check failed: {error}")
    print(f"COG structure OK: {args.file}")
    if args.gdal:
        validate_with_gdal(args.file)
    return 0


if __name__ == "__main__":
    sys.exit(main())
