#!/usr/bin/env python3
"""Read-only FM21 Touch person-record cross-save diagnostics.

Usage:
  python tools/analyze_person_records.py before.fmv after.fmv --json report.json

Requires tools/fm21_touch_container.py and system libzstd. Never modifies input saves.
This identifies person UID/entity joins only; it does not classify players or editable fields.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import re
import struct
from pathlib import Path
from typing import Any

import fm21_touch_container as fmv

NAME_CANDIDATE = re.compile(rb"(?=[\x02-\x60]\x00\x00\x00)")
DUPLICATED_U32 = re.compile(rb"(?=(.{4})\1)", re.DOTALL)
NAME_ENTRY_KIND = 5
MAX_ENTITY_ID = 1_000_000


def u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def parse_names(data: bytes) -> list[dict[str, Any]]:
    entries = []
    for match in NAME_CANDIDATE.finditer(data):
        p = match.start()
        length = data[p]
        start, tail = p + 4, p + 4 + length
        if tail + 21 > len(data) or u32(data, tail + 16) != NAME_ENTRY_KIND or data[tail + 20] != 0:
            continue
        uid = u32(data, tail + 8)
        if uid == 0 or uid != u32(data, tail + 12):
            continue
        try:
            name = data[start:tail].decode("utf-8")
        except UnicodeDecodeError:
            continue
        if not 2 <= len(name) <= 96 or not any(c.isalpha() for c in name) or any(ord(c) < 32 for c in name):
            continue
        entries.append({"name": name, "name_index_id": u32(data, tail + 4), "uid": uid})
    return entries


def map_entities(data: bytes, names: list[dict[str, Any]]) -> tuple[dict[int, dict[str, int]], set[int]]:
    wanted = {row["uid"] for row in names}
    candidates: dict[int, set[tuple[int, int]]] = collections.defaultdict(set)
    for match in DUPLICATED_U32.finditer(data):
        p = match.start()
        uid = u32(data, p)
        if uid not in wanted or p < 4 or p + 13 > len(data):
            continue
        if u32(data, p + 8) == NAME_ENTRY_KIND and data[p + 12] == 0:
            continue
        entity_id = u32(data, p - 4)
        if 1 <= entity_id <= MAX_ENTITY_ID:
            candidates[uid].add((entity_id, p - 4))
    resolved, ambiguous = {}, set()
    for uid, rows in candidates.items():
        if len(rows) == 1:
            entity_id, offset = next(iter(rows))
            resolved[uid] = {"entity_id": entity_id, "offset": offset}
        elif len(rows) > 1:
            ambiguous.add(uid)
    return resolved, ambiguous


def load_save(path: Path, zstd: Any) -> tuple[bytes, dict[str, Any]]:
    raw = path.read_bytes()
    footer, manifest = fmv.find_manifest(raw, zstd)
    save_name, top, nested = fmv.parse_manifest(manifest)
    members = {row["name"]: row for row in top + nested}
    if "game_db/.dat" not in members:
        raise ValueError(f"{path.name}: game_db/.dat missing")
    game_db, frames = fmv.extract_member(raw, members["game_db/.dat"], zstd)
    version = None
    if "game_info/.dat" in members:
        info, _ = fmv.extract_member(raw, members["game_info/.dat"], zstd)
        found = re.search(rb"[0-9]{2}\.[0-9]+\.[0-9]+-[0-9]+", info[:128])
        version = found.group().decode("ascii") if found else None
    return game_db, {
        "file": path.name,
        "file_bytes": len(raw),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "save_name": save_name,
        "database_version": version,
        "footer_offset": footer,
        "top_level_components": len(top),
        "nested_components": len(nested),
        "game_db_bytes": len(game_db),
        "game_db_frames": frames,
    }


def analyse(data: bytes) -> dict[str, Any]:
    names = parse_names(data)
    resolved, ambiguous = map_entities(data, names)
    by_id = {row["entity_id"]: row["offset"] for row in resolved.values()}
    tags, spans, rows = collections.Counter(), collections.Counter(), {}
    for name in names:
        uid = name["uid"]
        row = resolved.get(uid)
        if row is None:
            continue
        offset = row["offset"]
        tag = data[offset + 12:offset + 16].hex()
        next_offset = by_id.get(row["entity_id"] + 1)
        span = next_offset - offset if next_offset is not None and next_offset > offset else None
        if span is not None and 16 <= span <= 4096:
            spans[span] += 1
        else:
            span = None
        tags[tag] += 1
        rows[uid] = {**name, **row, "tag": tag, "span": span}
    return {
        "payload": data,
        "names": names,
        "rows": rows,
        "summary": {
            "name_index_entries": len(names),
            "resolved_uids": len(resolved),
            "ambiguous_uids": len(ambiguous),
            "unresolved_uids": len(names) - len(resolved),
            "distinct_record_tags": len(tags),
            "record_tags": dict(tags.most_common()),
            "adjacent_id_spans": {str(k): v for k, v in sorted(spans.items())},
        },
    }


def compare(a: dict[str, Any], b: dict[str, Any]) -> dict[str, Any]:
    names_a = {row["uid"]: row["name"] for row in a["names"]}
    names_b = {row["uid"]: row["name"] for row in b["names"]}
    common_names = set(names_a) & set(names_b)
    rows_a, rows_b = a["rows"], b["rows"]
    common = set(rows_a) & set(rows_b)
    changed = []
    same_entity_ids = 0
    for uid in sorted(common):
        left, right = rows_a[uid], rows_b[uid]
        same_entity_ids += left["entity_id"] == right["entity_id"]
        if left["span"] is None or right["span"] is None:
            continue
        length = min(left["span"], right["span"])
        x = a["payload"][left["offset"]:left["offset"] + length]
        y = b["payload"][right["offset"]:right["offset"] + length]
        diffs = [i for i, (v1, v2) in enumerate(zip(x, y)) if v1 != v2]
        if diffs:
            changed.append({
                "name": left["name"], "uid": uid, "entity_id": left["entity_id"],
                "before_offset": left["offset"], "after_offset": right["offset"],
                "before_tag": left["tag"], "after_tag": right["tag"],
                "before_span": left["span"], "after_span": right["span"],
                "changed_relative_offsets": diffs,
                "byte_changes": [{"offset": i, "before": x[i], "after": y[i]} for i in diffs],
            })
    return {
        "name_index": {
            "before_entries": len(names_a), "after_entries": len(names_b),
            "common_uids": len(common_names),
            "same_name_for_common_uids": sum(names_a[k] == names_b[k] for k in common_names),
        },
        "entity_mapping": {
            "before_resolved": len(rows_a), "after_resolved": len(rows_b),
            "common_resolved_uids": len(common), "same_entity_id_for_common_uids": same_entity_ids,
        },
        "changed_mapped_person_rows": changed,
        "player_classification": "NOT_PROVEN",
        "editable_fields": "NOT_PROVEN",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--json", type=Path)
    args = parser.parse_args()
    zstd = fmv.Zstd()
    before_data, before_meta = load_save(args.before, zstd)
    after_data, after_meta = load_save(args.after, zstd)
    before, after = analyse(before_data), analyse(after_data)
    report = {
        "read_only": True,
        "fixtures": {"before": before_meta, "after": after_meta},
        "before": before["summary"], "after": after["summary"],
        "comparison": compare(before, after),
        "limitations": [
            "Person UID/entity ID mapping is not player-vs-staff classification.",
            "Raw tags and adjacent-ID spans are diagnostics, not semantic record types.",
            "No CA, PA, attribute, finance, or other editable field is identified.",
            "A before/after match pair is not a controlled one-value experiment.",
        ],
    }
    encoded = json.dumps(report, indent=2, ensure_ascii=False) + "\n"
    if args.json:
        args.json.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
