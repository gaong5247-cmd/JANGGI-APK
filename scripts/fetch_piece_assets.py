#!/usr/bin/env python3
"""Download freely reusable, original Janggi piece pictures for offline GUIs.

Source: Wikimedia Commons, User:Yeo123, public domain (PD-self).
https://commons.wikimedia.org/wiki/Category:Janggi_pieces
Each image is a small drawing of a real-style octagonal Janggi piece with
traditional Chinese characters. This is *not* a photograph.
"""
import argparse
import hashlib
from pathlib import Path
from urllib.parse import quote
from urllib.request import Request, urlopen

DEST = Path(__file__).resolve().parents[1] / "app" / "assets" / "pieces"
NAMES = {
    "K": "Green King.png", "A": "Green Sa.png", "B": "Green Sang.png",
    "N": "Green Ma.png", "R": "Green Cha.png", "C": "Green Po.png",
    "P": "Green Zol.png", "k": "Red King.png", "a": "Red Sa.png",
    "b": "Red Sang.png", "n": "Red Ma.png", "r": "Red Cha.png",
    "c": "Red Po.png", "p": "Red Byung.png",
}

def url_for(title):
    canonical = title.replace(" ", "_")
    h = hashlib.md5(canonical.encode("utf-8")).hexdigest()
    return f"https://upload.wikimedia.org/wikipedia/commons/{h[0]}/{h[:2]}/{quote(canonical)}"

def fetch(strict=False):
    DEST.mkdir(parents=True, exist_ok=True)
    missing = []
    for piece, name in NAMES.items():
        path = DEST / (piece + ".png")
        if path.exists() and path.read_bytes().startswith(b"\\x89PNG\\r\\n\\x1a\\n"):
            continue
        try:
            req = Request(url_for(name), headers={"User-Agent": "JanggiLab/1.3 (GPLv3; piece asset redistribution from Wikimedia Commons)"})
            with urlopen(req, timeout=25) as response:
                data = response.read(512 * 1024 + 1)
            if len(data) < 100 or len(data) > 512 * 1024 or not data.startswith(b"\\x89PNG\\r\\n\\x1a\\n"):
                raise ValueError("Unexpected PNG asset")
            path.write_bytes(data)
            print(f"{piece}: {name} ({len(data)} bytes)")
        except Exception as exc:
            missing.append(name)
            print(f"WARNING: {name}: {exc}")
    if missing and strict:
        raise SystemExit("Cannot bundle all 14 offline piece images: " + ", ".join(missing))
    return not missing

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true", help="Require all 14 piece assets for the release package")
    fetch(parser.parse_args().strict)
