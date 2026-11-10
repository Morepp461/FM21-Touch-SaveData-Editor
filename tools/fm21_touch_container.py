#!/usr/bin/env python3
"""Read-only inspector for the FM Touch 2021 .fmv container.

This is a reverse-engineering prototype, not a save editor. It validates the
container and extracts named members without modifying the source file.
Requires libzstd (system Zstandard library) and Python 3.10+.
"""
from __future__ import annotations
import argparse, ctypes, ctypes.util, json, struct, sys
from pathlib import Path

MAGIC = b"\x28\xb5\x2f\xfd"
FMF_PREFIX = b"\x02\x01fmf.\x08\x00\x00"
OUTER_HEADER_SIZE = 30

class Zstd:
    def __init__(self):
        name = ctypes.util.find_library("zstd")
        if not name:
            raise RuntimeError("libzstd not found")
        self.lib = ctypes.CDLL(name)
        self.lib.ZSTD_findFrameCompressedSize.argtypes = [ctypes.c_void_p, ctypes.c_size_t]
        self.lib.ZSTD_findFrameCompressedSize.restype = ctypes.c_size_t
        self.lib.ZSTD_getFrameContentSize.argtypes = [ctypes.c_void_p, ctypes.c_size_t]
        self.lib.ZSTD_getFrameContentSize.restype = ctypes.c_ulonglong
        self.lib.ZSTD_decompress.argtypes = [ctypes.c_void_p, ctypes.c_size_t, ctypes.c_void_p, ctypes.c_size_t]
        self.lib.ZSTD_decompress.restype = ctypes.c_size_t
        self.lib.ZSTD_isError.argtypes = [ctypes.c_size_t]
        self.lib.ZSTD_isError.restype = ctypes.c_uint
        self.lib.ZSTD_getErrorName.argtypes = [ctypes.c_size_t]
        self.lib.ZSTD_getErrorName.restype = ctypes.c_char_p

    def frame_size(self, src: bytes) -> int:
        buf = ctypes.create_string_buffer(src)
        n = self.lib.ZSTD_findFrameCompressedSize(buf, len(src))
        if self.lib.ZSTD_isError(n):
            raise ValueError(self.lib.ZSTD_getErrorName(n).decode())
        return int(n)

    def decompress(self, src: bytes) -> bytes:
        sbuf = ctypes.create_string_buffer(src)
        expected = int(self.lib.ZSTD_getFrameContentSize(sbuf, len(src)))
        if expected in (0xFFFFFFFFFFFFFFFF, 0xFFFFFFFFFFFFFFFE):
            raise ValueError("frame has unknown/error content size")
        dbuf = ctypes.create_string_buffer(expected)
        n = self.lib.ZSTD_decompress(dbuf, expected, sbuf, len(src))
        if self.lib.ZSTD_isError(n):
            raise ValueError(self.lib.ZSTD_getErrorName(n).decode())
        return dbuf.raw[:n]


def u32(buf: bytes, p: int) -> tuple[int, int]:
    return struct.unpack_from("<I", buf, p)[0], p + 4

def u64(buf: bytes, p: int) -> tuple[int, int]:
    return struct.unpack_from("<Q", buf, p)[0], p + 8

def read_string(buf: bytes, p: int) -> tuple[str, int]:
    n, p = u32(buf, p)
    if p + n > len(buf):
        raise ValueError("manifest string exceeds bounds")
    return buf[p:p+n].decode("utf-8", "replace"), p+n

def read_member(buf: bytes, p: int) -> tuple[dict, int]:
    parts = []
    while True:
        part, p = read_string(buf, p)
        parts.append(part)
        if part.startswith("."):
            break
    offset, p = u64(buf, p)
    stored, p = u64(buf, p)
    plain, p = u64(buf, p)
    stamp1 = buf[p:p+8].hex(); stamp2 = buf[p+8:p+16].hex(); p += 16
    return {"name": "/".join(parts), "offset": offset, "stored_length": stored,
            "plain_length": plain, "stamp1": stamp1, "stamp2": stamp2}, p

def find_manifest(data: bytes, zstd: Zstd) -> tuple[int, bytes]:
    start = data.rfind(FMF_PREFIX)
    if start < OUTER_HEADER_SIZE:
        raise ValueError("footer manifest header not found")
    p = start + len(FMF_PREFIX)
    csize, p = u32(data, p)
    if p + csize != len(data):
        raise ValueError("footer compressed length does not match file end")
    frame = data[p:p+csize]
    if not frame.startswith(MAGIC):
        raise ValueError("footer is not a Zstandard frame")
    return start, zstd.decompress(frame)

def parse_manifest(buf: bytes) -> tuple[str, list[dict], list[dict]]:
    p = 0
    save_name, p = read_string(buf, p)
    count, p = u32(buf, p)
    top = []
    for _ in range(count):
        m, p = read_member(buf, p); top.append(m)
    sub_count, p = u32(buf, p)
    nested = []
    for _ in range(sub_count):
        name, p = read_string(buf, p)
        child_count, p = u32(buf, p)
        for _ in range(child_count):
            m, p = read_member(buf, p)
            m["archive"] = name
            nested.append(m)
    if buf[p:p+4] != b"\0\0\0\0":
        raise ValueError("manifest terminator missing")
    return save_name, top, nested

def extract_member(file_bytes: bytes, meta: dict, zstd: Zstd) -> tuple[bytes, int]:
    cur = OUTER_HEADER_SIZE + meta["offset"]
    end = cur + meta["stored_length"]
    if end > len(file_bytes):
        raise ValueError(f"{meta['name']}: member exceeds file bounds")
    output = bytearray(); frames = 0
    while len(output) < meta["plain_length"]:
        csize = zstd.frame_size(file_bytes[cur:end])
        frame = file_bytes[cur:cur+csize]
        output.extend(zstd.decompress(frame))
        cur += csize; frames += 1
        if len(output) < meta["plain_length"]:
            next_size = struct.unpack_from("<I", file_bytes, cur)[0]
            if next_size == 0 or cur + 4 + next_size > end:
                raise ValueError(f"{meta['name']}: invalid next-frame length")
            cur += 4
    if len(output) != meta["plain_length"]:
        raise ValueError(f"{meta['name']}: plaintext size mismatch")
    if cur != end - 4:
        raise ValueError(f"{meta['name']}: unexpected member boundary")
    return bytes(output), frames

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("save", type=Path)
    ap.add_argument("--list", action="store_true", help="list manifest members")
    ap.add_argument("--extract", help="extract a member by name, e.g. game_db/.dat")
    ap.add_argument("--out", type=Path, help="output path for --extract")
    args = ap.parse_args()
    raw = args.save.read_bytes()
    if len(raw) < OUTER_HEADER_SIZE or raw[:2] != b"\x02\x01" or raw[2:6] != b"fmf.":
        raise SystemExit("Not a recognised FMF/FMV container header")
    zstd = Zstd()
    footer_at, manifest = find_manifest(raw, zstd)
    save_name, top, nested = parse_manifest(manifest)
    print(f"Save: {save_name}")
    print(f"Bytes: {len(raw):,}; footer offset: {footer_at:,}")
    print(f"Top-level members: {len(top)}; nested members: {len(nested)}")
    all_members = top + nested
    if args.list or not args.extract:
        for m in all_members:
            print(f"{m['name']:<32} offset={m['offset']:>10,} stored={m['stored_length']:>10,} plain={m['plain_length']:>12,}")
    if args.extract:
        found = next((m for m in all_members if m["name"] == args.extract), None)
        if not found:
            raise SystemExit(f"Member not found: {args.extract}")
        content, frames = extract_member(raw, found, zstd)
        out = args.out or Path(found["name"].replace("/", "_"))
        out.write_bytes(content)
        print(f"Extracted {found['name']}: {len(content):,} bytes across {frames} Zstandard frame(s) -> {out}")

if __name__ == "__main__":
    main()
