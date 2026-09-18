#!/usr/bin/env python3
"""Linux: ANDROID_SDK_ROOT=/sdk JAVA_HOME=/jdk python3 scripts/build.py"""
import os,pathlib,shutil,subprocess
P=pathlib.Path(__file__).resolve().parents[1];B=P/'build'
sdk=pathlib.Path(os.environ['ANDROID_SDK_ROOT'])
ndk=sdk/'ndk'/'28.2.13676358';bin=ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin'
for abi,arch,compiler,comp in [('arm64-v8a','armv8','aarch64-linux-android26-clang++','ndk'),('x86_64','x86-64','x86_64-linux-android26-clang++','clang')]:
 src=B/('engine-'+abi)
 if not src.exists():shutil.copytree(P/'native/Fairy-Stockfish/src',src,ignore=shutil.ignore_patterns('*.o','stockfish'))
 subprocess.run(['make','-j'+str(min(4,os.cpu_count() or 2)),'build','ARCH='+arch,'COMP='+comp,'OS=Android','CXX='+str(bin/compiler),'largeboards=yes','nnue=no','EXTRACXXFLAGS=-fPIE','EXTRALDFLAGS=-static-libstdc++ -pie -Wl,-z,max-page-size=16384'],cwd=src,check=True)
 dest=B/'lib'/abi/'libfairy.so';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src/'stockfish',dest)
 subprocess.run([str(bin/'llvm-strip'),str(dest)],check=True)
subprocess.run(['python3',str(P/'scripts/package.py')],check=True)
