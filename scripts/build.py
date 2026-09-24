#!/usr/bin/env python3
"""Build Android engines and signed split/universal APKs. Requires SDK + NDK r28c."""
import argparse
import os
import pathlib
import shutil
import subprocess
import sys

P = pathlib.Path(__file__).resolve().parents[1]
B = P / 'build'
TARGETS = {
    'arm64-v8a': ('armv8', 'aarch64-linux-android26-clang++', 'ndk'),
    'armeabi-v7a': ('armv7', 'armv7a-linux-androideabi26-clang++', 'ndk'),
    'x86_64': ('x86-64', 'x86_64-linux-android26-clang++', 'clang'),
}
parser = argparse.ArgumentParser()
parser.add_argument('--abi', choices=['all', *TARGETS], default='all')
args = parser.parse_args()
sdk = pathlib.Path(os.environ['ANDROID_SDK_ROOT'])
ndk = pathlib.Path(os.environ.get('ANDROID_NDK_HOME', str(sdk / 'ndk' / '28.2.13676358')))
host = 'windows-x86_64' if os.name == 'nt' else 'linux-x86_64'
bin = ndk / 'toolchains/llvm/prebuilt' / host / 'bin'
make = os.environ.get('MAKE', 'mingw32-make' if os.name == 'nt' else 'make')
abis = list(TARGETS) if args.abi == 'all' else [args.abi]
def copy_source(source, destination):
    # Changed contents must get a fresh timestamp even when their original
    # timestamp predates an object from an earlier snapshot/build.
    target = pathlib.Path(destination)
    if not target.exists() or pathlib.Path(source).read_bytes() != target.read_bytes():
        shutil.copy(source, destination)
    return str(destination)

for abi in abis:
    arch, compiler, comp = TARGETS[abi]
    src = B / ('engine-' + abi)
    # Always refresh sources; previous code silently reused stale engine sources.
    shutil.copytree(P / 'native/Fairy-Stockfish/src', src, dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns('*.o', '*.tmp', 'stockfish*', '.depend'),
                    copy_function=copy_source)
    cxx = str(bin / compiler) if os.name != 'nt' else (bin / 'clang++.exe').as_posix() + ' --target=' + compiler[:-len('-clang++')]
    subprocess.run([make, '-j' + str(min(4, os.cpu_count() or 2)), 'build',
                    'ARCH=' + arch, 'COMP=' + comp, 'OS=Android', 'CXX=' + cxx,
                    'largeboards=yes', 'nnue=no', 'EXTRACXXFLAGS=-fPIE',
                    'EXTRALDFLAGS=-static-libstdc++ -pie -Wl,-z,max-page-size=16384'], cwd=src, check=True)
    dest = B / 'lib' / abi / 'libfairy.so'
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src / 'stockfish', dest)
    subprocess.run([str(bin / ('llvm-strip.exe' if os.name == 'nt' else 'llvm-strip')), str(dest)], check=True)
subprocess.run([sys.executable, str(P / 'scripts/package.py'), *abis], check=True)
