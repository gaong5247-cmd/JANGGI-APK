# 장기 연구실 1.1.0 (소스 수정본)

Android 8.0 이상용 오프라인 장기 앱. ARMv7/ARM64/x86_64 및 Universal APK 빌드 대상을 제공합니다.

**이번 수정본의 실제 완료/미완료 범위는 [AUDIT.md](AUDIT.md)를 참고하세요. APK/Windows EXE는 아직 생성되지 않았습니다.**

Termux에서 APK를 만들 때는 `python3 scripts/package-termux.py arm64-v8a`를 사용합니다. 이 경로는 전체 Android SDK/NDK, Gradle, Android Studio, NDK wrapper compiler를 요구하지 않습니다. Termux의 `clang++`, `javac`, `aapt2`, `d8`, `zipalign`, `apksigner`, `keytool`과 준비된 `android.jar`를 사용합니다. 현재 Termux 기기 자체가 ARM64이므로 이 경로는 ARM64 APK만 생성하며, `libc++_shared.so`도 APK에 함께 넣습니다. 앱은 설치 후 `libfairy.so`를 앱 전용 실행 파일로 복사하고 실행 권한을 설정합니다. 시작 실패 시 오류에 엔진 exit code와 마지막 출력이 표시됩니다.

## 설치와 사용

1. 빌드 후 `dist/JanggiLab-universal.apk` 또는 기기에 맞는 ABI별 APK를 휴대폰에서 열어 설치합니다. Android가 요청하면 해당 파일을 연 앱의 '알 수 없는 앱 설치'를 허용합니다.
2. 분석: `분석 시작`을 누르면 현재 차례 기준 평가, 추천수 화살표, 깊이, 초당 노드, 후보수 3개가 표시됩니다. 최대 10분 탐색하며 `중지`할 수 있습니다.
3. 직접 대국: 설정에서 내 진영을 선택하고 `대국 시작`을 누릅니다. 기물을 누르면 합법수 표시가 나타나고 목적지를 눌러 둡니다.
4. 엔진 대결: 설정에서 초 A / 한 B의 레벨과 한 수 생각 시간을 각각 선택하고 `대국 시작`을 누릅니다.
5. `이전/다음`으로 기보를 탐색합니다. 이전 국면에서 새로운 수를 두면 이후 수순을 대체합니다.
6. `새 대국`에서 양측 마·상 차림을 선택합니다. `포지션 편집 · 자유 배치`에서 기물을 배치·삭제하고 둘 차례를 정할 수 있으며, 적용 전 왕·궁성·기물 코드 검사를 수행합니다. `한 수 쉼`, `뒤집기`, `기보 공유`도 제공합니다.

기보와 설정은 로컬에 자동 저장됩니다. 네트워크 권한·광고·계정·서버·API 키가 없습니다. 앱을 떠나면 탐색을 멈추며, 다시 열어 시작할 수 있습니다. 기보 공유는 Android 공유 화면에서 사용자가 대상을 선택해야 합니다.

## 구현 범위

- 네이티브 Fairy-Stockfish 두 프로세스, janggi 공식 대회 규칙 변형. 합법수·장군·종국은 엔진으로 판정합니다.
- 엔진 대결은 **동일 엔진의 서로 다른 설정 간 대결**입니다. 외부 UCI 엔진 추가·리그 대회 자동 운영은 포함하지 않습니다.
- 레벨 0–20, 한 수 0.25–10초, 엔진별 해시 16–128 MB, 스레드 1/2/4.
- NNUE 가중치는 기본 포함하지 않습니다. 설정의 **장기 NNUE 파일 선택**에서 Fairy-Stockfish 장기용 파일을 가져오면 A/B 두 엔진에 적용합니다. 실패하면 기존 평가를 유지하며, **기본 평가로 복구** 버튼으로 내장 평가함수로 돌아갑니다. 최고 NNUE 버전과 동급 기력을 주장하지 않습니다.
- 사용자 기보의 현재 국면을 분석합니다. 외부 FEN/기보 불러오기, 임의 기물 편집 기능은 없습니다.
- 전화기 전체 위에 띄우는 오버레이나 잠금 화면 백그라운드 연속 분석은 제공하지 않습니다.
- 개인 설치용으로 서명된 APK입니다. Google Play 배포판은 아닙니다.

## 빌드

### Termux ARM64 (SDK/NDK 전체 설치 불필요)

```sh
cd ~/storage/downloads/JANGGI-APK
python3 scripts/package-termux.py arm64-v8a
```

`android.jar`가 표준 위치가 아니면 다음처럼 지정합니다. `aapt2`가 manifest 속성을 해석하려면 `framework-res.apk`도 필요하며, 스크립트가 `/system/framework/framework-res.apk` 등을 자동 탐색합니다.

```sh
ANDROID_JAR=/경로/android.jar python3 scripts/package-termux.py arm64-v8a
```

자동 탐색되지 않으면:

```sh
ANDROID_JAR=/경로/android.jar \
FRAMEWORK_RES=/경로/framework-res.apk \
python3 scripts/package-termux.py arm64-v8a
```

결과는 `dist/JanggiLab-arm64-v8a.apk`와 `dist/APK-SHA256.txt`입니다. `armeabi-v7a`와 `x86_64`는 별도 cross sysroot/toolchain 없이는 이 경로에서 만들지 않습니다.

Linux, Python 3, GNU Make, JDK 17, Android command line tools가 필요합니다.

```sh
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;28.2.13676358'
export ANDROID_SDK_ROOT=/path/to/sdk
export JAVA_HOME=/path/to/jdk-17
python3 scripts/build.py --abi all
```

Gradle나 외부 앱 라이브러리가 필요하지 않습니다. SDK/NDK 설치 이후 소스 빌드는 오프라인으로 가능합니다. 빌드가 생성한 `build/local-signing.p12`는 다음 버전 업데이트에 필요하므로 본인이 안전하게 보관하세요. 이 배포 소스에는 제작 시 사용한 개인 서명키가 포함되지 않습니다. 따라서 소스로 새로 빌드한 APK를 기존 APK 위에 설치하려면 서명이 달라 기존 앱 제거가 필요할 수 있으며, 제거하면 앱의 저장 기보가 사라집니다.

## 표시와 종국

- 일반 평가는 cp/100, `M5`/`-M3`는 탐색의 승리/패배 예측입니다.
- Fairy-Stockfish 장기는 외통 외 점수승도 mate로 출력하므로 M만으로 외통을 단정하지 않습니다.
- 현재 장군/빅장은 `appcheck`/`appbikjang`, 실제 종국은 `appresult`/`appreason`만 사용합니다.
- 기본 `janggi`는 빅장·반복·양측 한수쉼에서 점수 판정을 사용합니다. 전통 무승부 규칙으로 임의 변경하지 않았습니다.

## 빌드 산출물 및 자동화

`--abi all`은 `dist/JanggiLab-arm64-v8a.apk`, `JanggiLab-armeabi-v7a.apk`, `JanggiLab-x86_64.apk`, `JanggiLab-universal.apk`를 생성하도록 구성되어 있습니다. Universal에는 세 ABI가 들어갑니다. `tests/verify_apks.py`가 ABI와 ELF LOAD 16 KB 정렬을 검사합니다.

`.github/workflows/build.yml`은 Linux 검증, Android 패키징, Windows x86_64 엔진 빌드를 제공합니다. 저장소에 반영한 뒤 Actions에서 **Verify and build Janggi Lab**을 실행하세요. Windows 빌드는 MSYS2/MinGW를 사용하며 산출물은 UCI 엔진 `janggilab-engine-x86_64.exe`입니다.

각 CI 실행은 개인 설치용 새 서명키를 생성합니다. 같은 실행의 모든 APK는 같은 키로 서명되지만, 다른 실행 또는 기존 배포판과는 키가 다를 수 있습니다. 지속적인 앱 업데이트에는 소유자가 별도로 보관한 고정 서명키가 필요합니다. 서명키는 산출물에 포함하지 않습니다.

로컬 Windows 엔진 빌드 (MSYS2 MINGW64):

```sh
pacman -S --needed make mingw-w64-x86_64-gcc
cd native/Fairy-Stockfish/src
make -j4 build ARCH=x86-64 COMP=mingw largeboards=yes nnue=no EXTRALDFLAGS=-static
```

## 엔진 및 라이선스

Fairy-Stockfish upstream: https://github.com/fairy-stockfish/Fairy-Stockfish
정확한 커밋은 `ENGINE_REVISION`에 기록되어 있습니다.

전체 앱·엔진 소스는 GNU GPL v3 또는 이후 버전으로 배포합니다. `LICENSE`, `native/Fairy-Stockfish/Copying.txt`, `native/Fairy-Stockfish/AUTHORS`를 참고하세요. 엔진 원본에 `appstate` 명령을 추가하여 FEN, 합법수, 종국과 체크 상태를 앱에 전달했습니다. 수정된 전체 엔진 소스를 포함합니다. 보증은 제공되지 않습니다.

## 이번 수정본 검증

```sh
make -C native/Fairy-Stockfish/src -j4 build ARCH=x86-64 largeboards=yes nnue=no
python3 tests/engine_smoke.py native/Fairy-Stockfish/src/stockfish
python3 tests/janggi_regression.py native/Fairy-Stockfish/src/stockfish
python3 tests/upstream_perft.py native/Fairy-Stockfish/src/stockfish
javac --release 8 -encoding UTF-8 -d build/classes app/src/org/janggilab/Engine.java app/src/org/janggilab/AnalysisScore.java tests/EngineIntegration.java
java -cp build/classes org.janggilab.EngineIntegration /absolute/path/to/stockfish
```

`tests/audit-results/`에 이번 실행 결과가 있습니다. 유효한 NNUE가 있으면 Java 테스트의 두 번째 인자로 절대 경로를 넘겨 정상 로딩 경로도 검사할 수 있습니다. 파일명은 `janggi`로 시작해야 합니다.

## 기존 1.0.0 배포 당시 검증 (이번 APK 검증 아님)

`tests/results.json`: 실제 호스트 엔진에 대한 3개 장기 perft 국면, 한 수 쉼, 60수 엔진 대결, MultiPV와 중지·재동기화 검사 결과.

```sh
python3 tests/engine_smoke.py /path/to/linux/stockfish
```

ARM64 네이티브 엔진은 Android NDK r28c / API 26으로 빌드했습니다. ELF 16KB 페이지 정렬, APK 서명을 확인했습니다. 개별 실제 휴대폰에서의 동작 여부는 기기별로 확인이 필요합니다.

에뮬레이터의 부팅·패키지 서비스 문제로 APK 설치와 화면 실행 검증은 완료하지 못했습니다. 따라서 이 APK는 설치 및 실기기 동작 확인이 필요한 첫 빌드입니다. 자세한 확인 범위는 `tests/verification.md`를 참고하세요.
