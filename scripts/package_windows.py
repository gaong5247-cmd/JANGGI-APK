#!/usr/bin/env python3
"""Build a Windows GUI app-image and portable ZIP, bundling the native Janggi EXE.

Requires Windows 10/11, JDK 17+ with jpackage, and the x86_64 engine already
built into dist/janggilab-engine-x86_64.exe.
"""
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/"build"
DIST=ROOT/"dist"
CLASSES=BUILD/"desktop-classes"
INPUT=BUILD/"desktop-input"
ENGINE=DIST/"janggilab-engine-x86_64.exe"
SOURCES=[
    ROOT/"app/src/org/janggilab/Engine.java",
    ROOT/"app/src/org/janggilab/VariantConfig.java",
    ROOT/"app/src/org/janggilab/AnalysisScore.java",
    ROOT/"desktop/DesktopPosition.java",
    ROOT/"desktop/DesktopMain.java",
]
def run(*args):
    print(" ".join(map(str,args)),flush=True)
    subprocess.run(list(map(str,args)),check=True,cwd=ROOT)
def build():
    if not ENGINE.is_file():
        raise SystemExit("Missing Windows engine: "+str(ENGINE))
    shutil.rmtree(CLASSES,ignore_errors=True)
    shutil.rmtree(INPUT,ignore_errors=True)
    CLASSES.mkdir(parents=True)
    INPUT.mkdir(parents=True)
    run("javac","--release","8","-encoding","UTF-8","-d",CLASSES,*SOURCES,
        ROOT/"tests/DesktopPositionTest.java")
    run("java","-Djava.awt.headless=true","-cp",CLASSES,"org.janggilab.DesktopPositionTest")
    assets=ROOT/"app/assets/pieces"
    assets.mkdir(parents=True,exist_ok=True)
    jar=INPUT/"JanggiLab.jar"
    run("jar","--create","--file",jar,"--main-class","org.janggilab.DesktopMain",
        "-C",CLASSES,".","-C",ROOT/"app/assets","pieces")
    shutil.copy2(ENGINE,INPUT/ENGINE.name)
    shutil.rmtree(DIST/"JanggiLab",ignore_errors=True)
    run("jpackage","--type","app-image","--name","JanggiLab",
        "--app-version","1.3.0","--input",INPUT,"--dest",DIST,
        "--main-jar",jar.name,"--main-class","org.janggilab.DesktopMain",
        "--java-options","-Dfile.encoding=UTF-8")
    app=DIST/"JanggiLab"
    if not (app/"JanggiLab.exe").is_file():
        raise SystemExit("jpackage launcher missing")
    if not (app/"app"/ENGINE.name).is_file():
        raise SystemExit("bundled engine missing")
    archive=Path(shutil.make_archive(str(DIST/"JanggiLab-Windows-x86_64"),"zip",
                                      root_dir=DIST,base_dir="JanggiLab"))
    print("GUI ZIP:",archive)
    print("Standalone engine EXE:",ENGINE)
if __name__=="__main__":
    build()
