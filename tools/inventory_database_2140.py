#!/usr/bin/env python3
"""Read-only structural inventory for an extracted FM21 Touch 21.4.0 database.

The proprietary .dat inputs stay on the user's machine. This tool emits only
structural metadata (sizes, hashes, byte statistics, and short hex boundaries).
It does not print database names or copy source files into the repository.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

EXPECTED = (
    "basic_people_db.dat",
    "2140_fmc.xml",
    "lang_db.dat",
    "non_pl_hist_dt.dat",
    "non_pl_hist_id.dat",
    "non_pl_hist_index.dat",
    "people_db.dat",
    "pl_hist_dt.dat",
    "pl_hist_id.dat",
    "pl_hist_index.dat",
)


def entropy(data: bytes) -> float:
    if not data:
        return 0.0
    counts = Counter(data)
    length = len(data)
    return round(-sum((n / length) * math.log2(n / length) for n in counts.values()), 5)


def profile(path: Path) -> dict:
    digest = hashlib.sha256()
    counts = [0] * 256
    size = 0
    prefix = bytearray()
    suffix = bytearray()
    printable = 0
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
            size += len(chunk)
            if len(prefix) < 64:
                prefix.extend(chunk[: 64 - len(prefix)])
            suffix.extend(chunk)
            if len(suffix) > 64:
                del suffix[:-64]
            printable += sum(1 for value in chunk if value in (9, 10, 13) or 32 <= value <= 126)
            for value, amount in Counter(chunk).items():
                counts[value] += amount
    if size == 0:
        raise ValueError(f"Empty fixture: {path.name}")
    return {
        "file": path.name,
        "size_bytes": size,
        "sha256": digest.hexdigest(),
        "entropy_bits_per_byte": entropy_from_counts(counts, size),
        "printable_ascii_ratio": round(printable / size, 6),
        "zero_byte_ratio": round(counts[0] / size, 6),
        "distinct_byte_values": sum(1 for n in counts if n),
        "most_common_bytes": [
            {"byte": f"0x{value:02x}", "count": amount}
            for value, amount in sorted(enumerate(counts), key=lambda item: item[1], reverse=True)[:12]
        ],
        "prefix_hex_64": bytes(prefix).hex(),
        "suffix_hex_64": bytes(suffix).hex(),
    }


def entropy_from_counts(counts: list[int], size: int) -> float:
    return round(-sum((n / size) * math.log2(n / size) for n in counts if n), 5)


def parse_metadata(path: Path) -> dict:
    root = ET.parse(path).getroot()
    if root.tag != "properties":
        raise ValueError("Unexpected FMC XML root")
    values = {}
    for child in root:
        key = child.attrib.get("id")
        if key:
            values[key] = child.attrib.get("value", "")
    required = ("version", "required_version", "fmc", "fmm")
    missing = [key for key in required if not values.get(key)]
    if missing:
        raise ValueError("Missing metadata fields: " + ", ".join(missing))
    return {
        "version": values["version"],
        "required_version": values["required_version"],
        "fmc": values["fmc"] == "true",
        "fmm": values["fmm"] == "true",
        "author": values.get("author"),
        "is_selected_2140_target": (
            values["version"] == "21.4.0"
            and values["fmc"] == "true"
            and values["fmm"] == "false"
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("database_dir", type=Path, help="Directory containing the 10 extracted files")
    parser.add_argument("--json", type=Path, default=Path("database-2140-inventory.json"))
    args = parser.parse_args()
    root = args.database_dir
    missing = [name for name in EXPECTED if not (root / name).is_file()]
    if missing:
        parser.error("Missing required fixture(s): " + ", ".join(missing))

    report = {
        "tool": "fm21-touch-database-inventory",
        "read_only": True,
        "source_files_copied": False,
        "metadata": parse_metadata(root / "2140_fmc.xml"),
        "binary_files": [
            profile(root / name)
            for name in EXPECTED
            if name.endswith(".dat")
        ],
        "limitations": [
            "Byte statistics do not establish record boundaries or field semantics.",
            "No player/staff classification or CA/PA/attribute offsets are inferred.",
            "Do not use this inventory to authorize binary edits.",
        ],
    }
    encoded = json.dumps(report, indent=2, ensure_ascii=False) + "\n"
    args.json.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
