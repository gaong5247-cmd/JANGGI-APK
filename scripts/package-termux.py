#!/usr/bin/env python3
"""Build a signed ARM64 APK using only tools already installed in Termux."""
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build" / "termux-arm64"
DIST = ROOT / "dist"
PREFIX = Path(os.environ.get("PREFIX", "/data/data/com.termux/files/usr"))


def tool(name: str) -> str:
    value = os.environ.get(name.upper()) or shutil.which(name)
    if not value:
        raise SystemExit(f"필수 도구를 찾지 못했습니다: {name}")
    return value


def run(argv, cwd=None):
    argv = [str(x) for x in argv]
    print("+", " ".join(argv), flush=True)
    subprocess.run(argv, cwd=cwd, check=True)


def find_android_jar() -> Path:
    candidates = []
    explicit = os.environ.get("ANDROID_JAR")
    if explicit:
        candidates.append(Path(explicit).expanduser())
    candidates += [
        PREFIX / "share/java/android.jar",
        Path.home() / "android-sdk/platforms/android-35/android.jar",
        Path.home() / "android-sdk/platforms/android-34/android.jar",
        Path.home() / "android-sdk/platforms/android-33/android.jar",
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    for root in (PREFIX, Path.home()):
        try:
            matches = sorted(root.rglob("android.jar"), key=lambda p: len(str(p)))
        except OSError:
            matches = []
        if matches:
            return matches[0]
    raise SystemExit("android.jar를 찾지 못했습니다. ANDROID_JAR=/경로/android.jar 를 지정하세요.")


def find_framework_res() -> Path:
    explicit = os.environ.get("FRAMEWORK_RES")
    candidates = []
    if explicit:
        candidates.append(Path(explicit).expanduser())
    candidates += [
        PREFIX / "share/aapt2/framework-res.apk",
        PREFIX / "share/android/framework-res.apk",
        Path.home() / "android-sdk/platforms/android-35/framework-res.apk",
        Path.home() / "android-sdk/platforms/android-34/framework-res.apk",
        Path("/system/framework/framework-res.apk"),
        Path("/system_ext/framework/framework-res.apk"),
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise SystemExit(
        "aapt2용 framework-res.apk를 찾지 못했습니다. "
        "FRAMEWORK_RES=/경로/framework-res.apk 를 지정하세요."
    )


def main() -> None:
    target = sys.argv[1] if len(sys.argv) > 1 else "arm64-v8a"
    if target != "arm64-v8a":
        raise SystemExit("이 Termux 전용 경로는 ARM64만 지원합니다. armeabi-v7a/x86_64는 별도 cross toolchain이 필요합니다.")

    clang = tool("clang++")
    javac = tool("javac")
    aapt2 = tool("aapt2")
    d8 = tool("d8")
    zipalign = tool("zipalign")
    apksigner = tool("apksigner")
    keytool = tool("keytool")
    android_jar = find_android_jar()
    framework_res = find_framework_res()
    libcxx = Path(os.environ.get("LIBCXX_SHARED", str(PREFIX / "lib/libc++_shared.so"))).expanduser()
    if not libcxx.is_file():
        raise SystemExit(f"Termux libc++_shared.so를 찾지 못했습니다: {libcxx}")

    BUILD.mkdir(parents=True, exist_ok=True)
    DIST.mkdir(parents=True, exist_ok=True)
    engine_src = BUILD / "engine"
    shutil.copytree(ROOT / "native/Fairy-Stockfish/src", engine_src, dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns("*.o", "*.tmp", "stockfish", ".depend"))
    run(["make", "-j4", "build", "ARCH=armv8", "COMP=clang", "OS=Android", f"CXX={clang}",
         "largeboards=yes", "nnue=no", "EXTRACXXFLAGS=-fPIE -stdlib=libc++",
         "EXTRALDFLAGS=-stdlib=libc++ -pie -Wl,-z,max-page-size=16384"], cwd=engine_src)

    libdir = BUILD / "lib" / "arm64-v8a"
    libdir.mkdir(parents=True, exist_ok=True)
    engine = libdir / "libfairy.so"
    shutil.copy2(engine_src / "stockfish", engine)
    shutil.copy2(libcxx, libdir / "libc++_shared.so")

    classes = BUILD / "classes"
    dex = BUILD / "dex"
    resources = BUILD / "resources.zip"
    base = BUILD / "base.apk"
    for directory in (classes, dex):
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir(parents=True)
    java_sources = sorted((ROOT / "app/src").rglob("*.java"))
    run([javac, "--release", "8", "-encoding", "UTF-8", "-classpath", android_jar,
         "-d", classes, *java_sources])
    run([aapt2, "compile", "--dir", ROOT / "app/res", "-o", resources])
    run([aapt2, "link", "-o", base, "-I", android_jar, "-I", framework_res, "--manifest",
         ROOT / "app/AndroidManifest.xml", "-A", ROOT / "app/assets", resources])
    run([d8, "--lib", android_jar, "--min-api", "26", "--output", dex,
         *sorted(classes.rglob("*.class"))])

    unsigned = BUILD / "unsigned.apk"
    output = DIST / "JanggiLab-arm64-v8a.apk"
    shutil.copy2(base, unsigned)
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as archive:
        for item in sorted(dex.glob("*.dex")):
            archive.write(item, item.name)
        archive.write(engine, "lib/arm64-v8a/libfairy.so")
        archive.write(libcxx, "lib/arm64-v8a/libc++_shared.so")

    key = BUILD / "local-signing.p12"
    if not key.exists():
        run([keytool, "-genkeypair", "-keystore", key, "-storetype", "PKCS12",
             "-storepass", "localbuild", "-alias", "janggilab", "-keyalg", "RSA",
             "-keysize", "3072", "-validity", "10000", "-dname", "CN=Janggi Lab Local"])
    aligned = BUILD / "aligned.apk"
    run([zipalign, "-f", "-P", "16", "4", unsigned, aligned])
    run([apksigner, "sign", "--ks", key, "--ks-pass", "pass:localbuild", "--out", output, aligned])
    run([apksigner, "verify", "--verbose", output])
    run([zipalign, "-c", "-P", "16", "4", output])
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    (DIST / "APK-SHA256.txt").write_text(f"{digest}  {output.name}\n", encoding="utf-8")
    print(f"완료: {output}")
    print(f"SHA256: {digest}")


if __name__ == "__main__":
    main()
