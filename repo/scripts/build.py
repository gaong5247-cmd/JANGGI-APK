#!/usr/bin/env python3
"""Build Android engines and signed split/universal APKs. Requires SDK + NDK r28c."""
import argparse
import os
import pathlib
import shutil
import subprocess

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
bin = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
abis = list(TARGETS) if args.abi == 'all' else [args.abi]
for abi in abis:
    arch, compiler, comp = TARGETS[abi]
    src = B / ('engine-' + abi)
    # Always refresh sources; previous code silently reused stale engine sources.
    shutil.copytree(P / 'native/Fairy-Stockfish/src', src, dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns('*.o', '*.tmp', 'stockfish*', '.depend'))
    subprocess.run(['make', '-j' + str(min(4, os.cpu_count() or 2)), 'build',
                    'ARCH=' + arch, 'COMP=' + comp, 'OS=Android', 'CXX=' + str(bin / compiler),
                    'largeboards=yes', 'nnue=no', 'EXTRACXXFLAGS=-fPIE',
                    'EXTRALDFLAGS=-static-libstdc++ -pie -Wl,-z,max-page-size=16384'], cwd=src, check=True)
    dest = B / 'lib' / abi / 'libfairy.so'
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src / 'stockfish', dest)
    subprocess.run([str(bin / 'llvm-strip'), str(dest)], check=True)
subprocess.run(['python3', str(P / 'scripts/package.py'), *abis], check=True)
