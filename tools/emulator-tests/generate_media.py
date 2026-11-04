#!/usr/bin/env python3
"""Make unique-pixel PNG files on the host, not with 50k slow emulator Bitmap.compress calls.

Every image carries a source-qualified 32-bit identity in a stable 8x8
grid of 4x4-pixel blocks. These exact RGBA values are used by the
instrumentation drawable-vs-file pixel comparison. Standard-library-only.
"""
import argparse
import io
import json
import pathlib
import struct
import tarfile
import time
import zlib

ROOT = "GalleryCI"
ON = bytes((228, 42, 86, 255))
OFF = bytes((26, 72, 184, 255))
PNG_HEADER = b"\x89PNG\r\n\x1a\n"

def chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

def png(source: str, index: int) -> bytes:
    key = (ord(source) << 20) | (index + 1)
    pixels = bytearray()
    for y in range(32):
        pixels.append(0)  # PNG RGBA scanline filter None
        for x in range(32):
            pixels.extend(ON if (key >> ((y // 4) * 8 + x // 4)) & 1 else OFF)
    return (PNG_HEADER +
            chunk(b"IHDR", struct.pack(">IIBBBBB", 32, 32, 8, 6, 0, 0, 0)) +
            chunk(b"tEXt", b"GalleryCI\x00" + f"{source}:{index}".encode()) +
            chunk(b"IDAT", zlib.compress(bytes(pixels), level=1)) +
            chunk(b"IEND", b""))

def add(archive, path, payload, modified):
    item = tarfile.TarInfo(path)
    item.mode = 0o644
    item.size = len(payload)
    item.mtime = modified
    archive.addfile(item, io.BytesIO(payload))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scale", choices=("smoke", "large", "segment", "ceiling"), required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--manifest", required=True)
    args = parser.parse_args()
    sizes = {
        "smoke": {"A": 64, "N": 64, "P": 64},
        "large": {"A": 12011, "N": 12013, "P": 12017},
        "segment": {"A": 20501, "N": 96, "P": 96},
        "ceiling": {"A": 50009, "N": 96, "P": 96},
    }[args.scale]
    start = time.monotonic()
    pathlib.Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(args.output, "w") as archive:
        add(archive, ROOT + "/N/.nomedia", b"", 1700000000)
        for source, size in sizes.items():
            for index in range(size):
                filename = f"IMG_{index}.png" if index % 4 == 0 else f"IMG_{index:06d}.png"
                modified = 1578000000 + ((index * 7919 + ord(source)) % 180000) * 60
                add(archive, f"{ROOT}/{source}/{filename}", png(source, index), modified)
        add(archive, ROOT + "/.ci_import_ready", args.scale.encode(), 1700000000)
    manifest = {"scale": args.scale, "pngPerSource": sizes, "totalPng": sum(sizes.values()),
                "pixel": "unique 32-bit source-qualified 8x8 marker, 32x32 RGBA",
                "durationSeconds": round(time.monotonic() - start, 3)}
    pathlib.Path(args.manifest).write_text(json.dumps(manifest, indent=2))
    print(json.dumps(manifest), flush=True)

if __name__ == "__main__":
    main()
