#!/usr/bin/env python3
"""Bundle corresponding sources for the pinned Stockless ARM64 APK engine."""
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "build" / "stockless"
DIST = ROOT / "dist"
PIN = "99067d0964e40285dd5c24de9c0ecb666dc31924"
SKIP_DIRS = {".git", "__pycache__", ".pytest_cache", ".venv", "venv"}
SKIP_SUFFIXES = {".o", ".obj", ".a", ".so", ".dll", ".exe", ".pyc", ".pyd", ".pyo", ".nnue", ".svnnue"}
SKIP_NAMES = {"stockless-variants", "stockfish", "stockfish.exe", ".depend"}

if not SOURCE.is_dir():
    raise SystemExit(f"Missing checked-out Stockless sources: {SOURCE}")
commit = subprocess.check_output(
    ["git", "-C", str(SOURCE), "rev-parse", "HEAD"], text=True
).strip()
if commit != PIN:
    raise SystemExit(f"Unexpected Stockless revision: {commit}")

DIST.mkdir(exist_ok=True)
destination = DIST / "JanggiLab-Stockless-Corresponding-Source.zip"
count = 0
with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as out:
    for path in sorted(SOURCE.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(SOURCE)
        if any(piece in SKIP_DIRS for piece in rel.parts):
            continue
        if rel.name in SKIP_NAMES or path.suffix in SKIP_SUFFIXES:
            continue
        if path.stat().st_size > 40 * 1024 * 1024:
            raise SystemExit(f"Unusually large source file: {path}")
        out.write(path, "Stockless-variants/" + rel.as_posix())
        count += 1
    out.writestr("BUILD-PROVENANCE.txt",
                 f"Stockless Variants pin: {PIN}\n"
                 "Materialization: python tools/materialize.py\n"
                 "Android: make -C build/engine/src build COMP=ndk ARCH=armv8 "
                 "CXX=aarch64-linux-android26-clang++ largeboards=yes all=yes "
                 "nnue=no EXE=stockless-variants\n"
                 "APK: JanggiLab 1.3.0, source: "
                 "https://github.com/gaong5247-cmd/JANGGI-APK\n")
print(f"Packaged {count} source files: {destination} ({destination.stat().st_size:,} bytes)")
