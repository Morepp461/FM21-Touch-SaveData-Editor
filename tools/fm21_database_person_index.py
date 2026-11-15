#!/usr/bin/env python3
"""Read-only parser for the FM21 Touch 21.4 people database name/UID index.

The parser emits counts and hashes only. It never writes to or modifies source
.dat files and does not infer player/staff type or editable attribute offsets.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

from fm21_touch_container import Zstd

DAT_SIGNATURE = b"\x03\x01tad."
ZSTD_MAGIC = b"\x28\xb5\x2f\xfd"
NAME_MARKER = b"\x05\x00\x00\x00\x00"
MIN_UID = 1_000_000_000
NAME_CANDIDATE = re.compile(rb"(?=[\x02-\x60]\x00\x00\x00)")


def u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def decompress_chunked_dat(path: Path, zstd: Zstd) -> tuple[bytes, int]:
    raw = path.read_bytes()
    if not raw.startswith(DAT_SIGNATURE) or len(raw) < 16:
        raise ValueError(f"{path.name}: unsupported TAD database wrapper")
    cursor = 12
    output = bytearray()
    frames = 0
    while cursor < len(raw):
        if raw[cursor:cursor + 4] != ZSTD_MAGIC:
            raise ValueError(f"{path.name}: expected Zstandard frame at {cursor}")
        frame_size = zstd.frame_size(raw[cursor:])
        frame = raw[cursor:cursor + frame_size]
        output.extend(zstd.decompress(frame))
        cursor += frame_size
        frames += 1
        if cursor == len(raw):
            break
        if cursor + 4 > len(raw):
            raise ValueError(f"{path.name}: truncated next-frame length")
        declared_size = u32(raw, cursor)
        cursor += 4
        if declared_size <= 0 or cursor + declared_size > len(raw):
            raise ValueError(f"{path.name}: invalid next-frame length")
        actual_size = zstd.frame_size(raw[cursor:])
        if declared_size != actual_size:
            raise ValueError(f"{path.name}: next-frame length mismatch")
    return bytes(output), frames


def plausible_name(raw: bytes) -> str | None:
    if not 2 <= len(raw) <= 96 or any(value < 0x20 for value in raw):
        return None
    try:
        name = raw.decode("utf-8")
    except UnicodeDecodeError:
        return None
    if not name or not any(char.isalpha() for char in name):
        return None
    if any(ord(char) < 32 for char in name):
        return None
    return name


def parse_people_name_uid(data: bytes) -> dict[int, int]:
    """Return name-index-ID -> UID using the observed 21.4 name record pattern."""
    result: dict[int, int] = {}
    cursor = 0
    while True:
        marker_at = data.find(NAME_MARKER, cursor)
        if marker_at < 4:
            break
        cursor = marker_at + 1
        if marker_at + 9 > len(data):
            continue
        length = u32(data, marker_at + 5)
        if not 2 <= length <= 96:
            continue
        name_start = marker_at + 9
        name_end = name_start + length
        tail = name_end
        if tail + 16 > len(data) or data[tail:tail + 4] != b"\0\0\0\0":
            continue
        name_id_before = u32(data, marker_at - 4)
        name_id_after = u32(data, tail + 4)
        uid = u32(data, tail + 8)
        uid_copy = u32(data, tail + 12)
        if name_id_before != name_id_after or uid <= MIN_UID or uid != uid_copy:
            continue
        if plausible_name(data[name_start:name_end]) is None:
            continue
        previous = result.get(name_id_before)
        if previous is None or previous == uid:
            result[name_id_before] = uid
        else:
            result.pop(name_id_before, None)
    return result


def parse_basic_people_uid_index(data: bytes) -> dict[int, int]:
    """Return name-index-ID -> UID from the observed basic-people index pattern."""
    result: dict[int, int] = {}
    for offset in range(0, len(data) - 18):
        if data[offset + 12] != 0x11 or data[offset + 13:offset + 18] != b"\x05\0\0\0\0":
            continue
        name_id = u32(data, offset)
        uid = u32(data, offset + 4)
        uid_copy = u32(data, offset + 8)
        if uid <= MIN_UID or uid != uid_copy:
            continue
        previous = result.get(name_id)
        if previous is None or previous == uid:
            result[name_id] = uid
        else:
            result.pop(name_id, None)
    return result


def parse_save_uid_set(data: bytes) -> set[int]:
    """Extract UIDs from the observed FM21 save game_db name-index entries."""
    result: set[int] = set()
    for match in NAME_CANDIDATE.finditer(data):
        offset = match.start()
        length = data[offset]
        tail = offset + 4 + length
        if tail + 21 > len(data) or u32(data, tail + 16) != 5 or data[tail + 20] != 0:
            continue
        uid = u32(data, tail + 8)
        if uid > 0 and uid == u32(data, tail + 12):
            name = data[offset + 4:tail]
            if plausible_name(name) is not None:
                result.add(uid)
    return result


def parse_metadata(path: Path) -> dict[str, Any]:
    root = ET.parse(path).getroot()
    if root.tag != "properties":
        raise ValueError("Unexpected FMC XML root")
    values = {node.attrib.get("id"): node.attrib.get("value", "") for node in root}
    required = ("version", "required_version", "fmc", "fmm")
    if any(not values.get(key) for key in required):
        raise ValueError("FMC metadata is incomplete")
    return {
        "version": values["version"],
        "required_version": values["required_version"],
        "fmc": values["fmc"] == "true",
        "fmm": values["fmm"] == "true",
        "is_2140_target": values["version"] == "21.4.0" and values["fmc"] == "true" and values["fmm"] == "false",
    }


def file_digest(path: Path) -> dict[str, Any]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
            size += len(chunk)
    return {"file": path.name, "size_bytes": size, "sha256": digest.hexdigest()}


def analyse(database_dir: Path, save_game_db: Path | None = None) -> dict[str, Any]:
    metadata_path = database_dir / "2140_fmc.xml"
    people_path = database_dir / "people_db.dat"
    basic_path = database_dir / "basic_people_db.dat"
    for path in (metadata_path, people_path, basic_path):
        if not path.is_file():
            raise FileNotFoundError(path)
    zstd = Zstd()
    people_raw, people_frames = decompress_chunked_dat(people_path, zstd)
    basic_raw, basic_frames = decompress_chunked_dat(basic_path, zstd)
    people_index = parse_people_name_uid(people_raw)
    basic_index = parse_basic_people_uid_index(basic_raw)
    joined = {key: value for key, value in people_index.items() if basic_index.get(key) == value}
    save_uid_count = None
    save_matches = None
    if save_game_db is not None:
        save_uids = parse_save_uid_set(save_game_db.read_bytes())
        save_uid_count = len(save_uids)
        save_matches = len(set(people_index.values()) & save_uids)
    return {
        "tool": "fm21-touch-2140-person-index",
        "read_only": True,
        "metadata": parse_metadata(metadata_path),
        "inputs": [file_digest(metadata_path), file_digest(people_path), file_digest(basic_path)],
        "decompression": {
            "people_db_frames": people_frames,
            "people_db_plain_bytes": len(people_raw),
            "basic_people_db_frames": basic_frames,
            "basic_people_db_plain_bytes": len(basic_raw),
        },
        "index": {
            "people_name_uid_entries": len(people_index),
            "basic_people_uid_entries": len(basic_index),
            "cross_table_uid_matches": len(joined),
            "save_game_db_uid_count": save_uid_count,
            "database_uid_matches_in_save": save_matches,
        },
        "limitations": [
            "This is a conservative name/UID index extraction, not a complete person-table decoder.",
            "UID matches do not classify a person as player or staff.",
            "The index does not expose CA, PA, individual attribute offsets, scales, or write semantics.",
            "Do not use this report to authorize edited-save writes.",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("database_dir", type=Path, help="Directory with 2140_fmc.xml, people_db.dat and basic_people_db.dat")
    parser.add_argument("--game-db", type=Path, help="Optional extracted game_db/.dat payload from a save")
    parser.add_argument("--json", type=Path, help="Optional JSON report destination")
    args = parser.parse_args()
    report = analyse(args.database_dir, args.game_db)
    encoded = json.dumps(report, indent=2, ensure_ascii=False) + "\n"
    if args.json:
        args.json.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
