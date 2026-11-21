#!/usr/bin/env python3
"""Rasterize 14 attribution-required wood-style Janggi piece images for offline use.

Source: Kadagaden/chess-pieces, janggi_wooden/*.svg
Licensed under Creative Commons Attribution 4.0 International (CC BY 4.0).
Source and license links are recorded in app/assets/PIECE-CREDITS.md.

For CI, check out Kadagaden/chess-pieces into build/piece-upstream and
install librsvg2-bin (rsvg-convert). Output is bundled inside the APK/JAR.
This avoids network access during packaging, image-host rate limiting,
SVG rendering differences at runtime, and CJK glyph font dependencies.
"""
from pathlib import Path
import argparse
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"build"/"piece-upstream"/"janggi_wooden"
DEST=ROOT/"app"/"assets"/"pieces"
KINDS={"K":"king","A":"advisor","B":"elephant","N":"horse",
       "R":"chariot","C":"cannon","P":"pawn"}

def build(strict=False):
    DEST.mkdir(parents=True,exist_ok=True)
    renderer=shutil.which("rsvg-convert")
    missing=[]
    for prefix,color in [("", "blue"),("red", "red")]:
        for kind,name in KINDS.items():
            letter=kind if color=="blue" else kind.lower()
            src=SOURCE/(color+"_"+name+".svg")
            out=DEST/(letter+".png")
            if src.is_file() and renderer:
                subprocess.run([renderer,"--width","220","--height","220",
                                "--output",str(out),str(src)],check=True)
            if not out.is_file() or not out.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
                missing.append(str(src))
                continue
            print(f"{letter}: {out.stat().st_size} bytes",flush=True)
    if missing and strict:
        raise SystemExit("Missing wood-style Chinese-character piece sprites: "+
                         ", ".join(missing)+"; install librsvg2-bin and check out "+str(SOURCE))
    if missing:
        print("WARNING: missing sprites; legacy hand-drawn CJK fallback remains active")
    return not missing

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--strict",action="store_true")
    build(parser.parse_args().strict)
