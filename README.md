# 장기 연구실 1.2.0

Android 8.0 이상용 오프라인 장기 앱. ARMv7/ARM64/x86_64 및 Universal APK 빌드 대상을 제공합니다.

장기 규칙 4종과 이동·잡기·장군 효과음을 지원합니다. 이전 버전 감사 기록은 [AUDIT.md](AUDIT.md)이며, 이번 변경은 아래 규칙·검증 설명을 기준으로 합니다.

Termux에서 APK를 만들 때는 `python3 scripts/package-termux.py arm64-v8a`를 사용합니다. 이 경로는 전체 Android SDK/NDK, Gradle, Android Studio, NDK wrapper compiler를 요구하지 않습니다. Termux의 `clang++`, `javac`, `aapt2`, `d8`, `zipalign`, `apksigner`, `keytool`과 준비된 `android.jar`를 사용합니다. 현재 Termux 기기 자체가 ARM64이므로 이 경로는 ARM64 APK만 생성하며, `libc++_shared.so`도 APK에 함께 넣습니다. 앱은 Android가 추출한 native library 경로의 ELF를 비트 수에 맞는 시스템 linker로 실행합니다. 시작 실패 시 오류에 엔진 exit code와 마지막 출력이 표시됩니다.

## 설치와 사용

1. 빌드 후 `dist/JanggiLab-universal.apk` 또는 기기에 맞는 ABI별 APK를 휴대폰에서 열어 설치합니다. Android가 요청하면 해당 파일을 연 앱의 '알 수 없는 앱 설치'를 허용합니다.
2. 분석: `분석 시작`을 누르면 현재 차례 기준 평가, 추천수 화살표, 깊이, 초당 노드, 후보수 3개가 표시됩니다. 최대 10분 탐색하며 `중지`할 수 있습니다.
3. 직접 대국: 설정에서 내 진영을 선택하고 `대국 시작`을 누릅니다. 기물을 누르면 합법수 표시가 나타나고 목적지를 눌러 둡니다.
4. 엔진 대결: 설정에서 초 A / 한 B의 레벨과 한 수 생각 시간을 각각 선택하고 `대국 시작`을 누릅니다.
5. `이전/다음`으로 기보를 탐색합니다. 이전 국면에서 새로운 수를 두면 이후 수순을 대체합니다.
6. `새 대국`에서 양측 마·상 차림을 선택합니다. `포지션 편집 · 자유 배치`에서 기물을 배치·삭제하고 둘 차례를 정할 수 있으며, 적용 전 왕·궁성·기물 코드 검사를 수행합니다. `한 수 쉼`, `뒤집기`, `기보 공유`도 제공합니다.

기보와 설정은 로컬에 자동 저장됩니다. 네트워크 권한·광고·계정·서버·API 키가 없습니다. 앱을 떠나면 탐색을 멈추며, 다시 열어 시작할 수 있습니다. 기보 공유는 Android 공유 화면에서 사용자가 대상을 선택해야 합니다.

## 구현 범위

- 네이티브 Fairy-Stockfish 두 프로세스, 기존 `janggi`와 같은 9×10 기물 이동을 공유하는 장기 규칙 4종. 합법수·장군·종국은 엔진으로 판정합니다.
- 엔진 대결은 **동일 엔진의 서로 다른 설정 간 대결**입니다. 외부 UCI 엔진 추가·리그 대회 자동 운영은 포함하지 않습니다.
- 레벨 0–20, 한 수 0.25–10초, 엔진별 해시 16–128 MB, 스레드 1/2/4.
- NNUE 가중치는 기본 포함하지 않습니다. 설정의 **장기 NNUE 파일 선택**에서 Fairy-Stockfish 장기용 파일을 가져오면 A/B 두 엔진에 적용합니다. 실패하면 기존 평가를 유지하며, **기본 평가로 복구** 버튼으로 내장 평가함수로 돌아갑니다. 최고 NNUE 버전과 동급 기력을 주장하지 않습니다.
- 사용자 기보의 현재 국면을 분석합니다. 외부 FEN/기보 파일 불러오기는 지원하지 않습니다. 자유 배치 편집은 지원합니다.
- 전화기 전체 위에 띄우는 오버레이나 잠금 화면 백그라운드 연속 분석은 제공하지 않습니다.
- 개인 설치용으로 서명된 APK입니다. Google Play 배포판은 아닙니다.

## 장기 규칙 선택

**설정 → 장기 규칙 → 적용**에서 선택합니다. 변경 시 진행 중 탐색을 중단하고 두 엔진 A/B에 같은 규칙을 적용한 후 새 대국으로 초기화합니다. 현재 기보는 지워지므로 필요하면 먼저 공유하세요. 선택과 기보는 SharedPreferences에 함께 저장되며 재실행 후 복원됩니다. `새 대국`은 선택한 규칙의 엔진 반환 `appstartfen`을 바탕으로 양측 마·상 차림을 적용합니다.

| Variant | 표시 이름 | 빅장 | 한 수 쉼 | 반복 | 점수 판정 |
| --- | --- | --- | --- | --- | --- |
| `janggitraditional` | 전통 장기 | 있음 | 있음 | 동일 국면 3회, 연속 장군 제재 | 장기 점수 |
| `janggimodern` | 현대 장기 | 없음 | 있음 | 왕복 반복 금지 + 동일 국면 4회, 연속 장군 제재 | 장기 점수 |
| `jangginopass` | 패스 없는 장기 | 기본 장기 기준 | 없음 | 동일 국면 3회, 연속 장군 제재 | 기본 장기 점수 |
| `janggiblitz` | 블리츠 장기 | 없음 | 있음 | 동일 국면 2회에 즉시 무승부 | 없음 |

사용자가 요청한 전통 장기는 점수 판정을 **켜며**, upstream의 같은 ID(점수 없는 전통 장기)와 의도적으로 다릅니다. 기본 `janggi`의 이동·빅장·패스·판정은 그대로 유지됩니다. 현대 장기의 “엄격”은 단순히 반복 횟수를 줄이는 뜻이 아니라 기존 Fairy-Stockfish 현대 장기의 `moveRepetitionIllegal` 규칙을 뜻합니다. 엔진은 특정 왕복 반복을 `move_repetition` 규칙패로 처리하며, 별도의 일반 반복은 4회에서 점수 판정합니다. 단순 양측 마 왕복 테스트에서는 9번째 반수에 반복한 쪽의 규칙패가 발생합니다.

실제 INI 옵션은 다음과 같습니다. 모두 `parser.cpp`가 지원하는 이름입니다.

| 옵션 | traditional | modern | nopass | blitz |
| --- | --- | --- | --- | --- |
| `bikjangRule` | true | false | true | false |
| `pass` (양측) | true | true | false | true |
| `nFoldRule` | 3 | 4 | 3 | 2 |
| `nFoldValue` | draw | draw | draw | draw |
| `perpetualCheckIllegal` | true | true | true | false |
| `materialCounting` | janggi | janggi | janggi | none |
| `moveRepetitionIllegal` | false | true | false | false |
| `nMoveRule` (무진행 전수) | 50 | 100 | 50 | 50 |

`variants.ini`의 `[이름:janggi]` 블록이 설정 원본입니다. `scripts/embed_variants.py`로 생성한 `janggi_variants.inc`를 엔진이 같은 INI 파서로 읽어 등록합니다. 따라서 Android와 Windows EXE 모두 외부 INI 경로 설정 없이 바로 4개 ID를 사용할 수 있습니다. 수정 후 생성 스크립트를 실행하고 `--check`로 동기화를 확인하세요. `stockfish check variants.ini`는 내장된 ID도 설정 문법을 검증합니다. `nnueAlias`는 지원되는 INI 옵션이 **아니므로 사용하지 않습니다**. 장기용 NNUE 호환성은 `janggi_variant()`의 내부 필드에서 상속합니다.

### 반복과 점수 판정의 정확한 의미

- 블리츠의 `nFoldRule=2`는 같은 차례·기물 배치·엔진 상태 키가 **최초 국면을 포함해 두 번 등장**하면 무승부라는 뜻입니다. `Position::is_optional_game_end`는 최소 4반수 전부터 이력을 비교합니다. 예: `b1c3 b10c8 c3b1 c8b10` 직후 무승부. 탐색 내부의 반복 예상과 달리 앱 판정은 `ply=0`의 실제 이력에만 근거합니다. 블리츠는 연속 장군도 반복 시 무승부이며, 연속 두 패스는 별도 `double_pass` 무승부입니다. 임의 FEN만으로는 이전 반복 이력을 복원할 수 없어 앱은 초기 FEN과 수순을 함께 전송합니다.
- `nFoldValue=draw`여도 `materialCounting=janggi`이면 최종 결과가 점수승으로 바뀝니다. 이 엔진은 차 13, 포 7, 마 5, 상 3, 사 3, 병 2점을 사용하며 **초 점수 − 한 점수 − 1 > 0이면 초 승, 아니면 한 승**으로 구현합니다 (`position.h::material_counting_result`). 정수 기물 점수에서 한 1.5점 덤과 같은 승패 경계입니다. 외통·연속 장군·반복 금지 규칙패는 이 일반 점수 판정과 구분됩니다.
- 전통/기본 장기의 빅장은 장군과 별개입니다. 빅장 수락, 연속 패스, 일반 반복, 무진행 수 제한은 엔진 구현대로 점수 판정합니다. 패스 없음은 빅장 표시를 유지하지만 패스로 수락할 수 없으며, 합법수가 없으면 엔진의 stalemate 규칙패입니다. 현대/블리츠는 `appbikjang=0`이며 빅장 UI가 승패에 관여하지 않습니다.
- “한 수 쉼”은 현재 합법수 중 왕의 제자리 수가 있을 때만 전송합니다. 패스 없음에서는 버튼이 비활성화되고 메서드도 차단됩니다. 종국 판정 후 모든 variant에서 착수를 막습니다. 탐색의 `score mate`는 실제 외통 판정으로 사용하지 않습니다.

### 효과음

설정에서 **기물 이동 · 잡기 · 장군 효과음**을 켜고 끌 수 있으며 설정이 저장됩니다. 이동은 짧은 타격음, 잡기는 낮고 긴 타격음, 장군은 두 음의 알림음입니다. 실제 사람/엔진 착수에만 재생하며 기보 탐색·새 대국·규칙 변경·패스에는 재생하지 않습니다. 장군 음은 착수 후 엔진의 `appcheck` 확인 시 추가됩니다. 녹음 음성은 아니며, 앱에 포함된 PCM 음원으로 오프라인 재생합니다. `scripts/generate_sounds.py`로 재생성할 수 있는 직접 합성한 음원입니다.

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

Linux 또는 Windows(MSYS2의 GNU Make와 Unix 도구를 PATH에 추가), Python 3, JDK 17, Android SDK/NDK가 필요합니다. Windows에서는 `mingw32-make`를 사용하며 `MAKE` 환경변수로 경로를 지정할 수 있습니다.

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
python3 scripts/embed_variants.py --check
python3 tests/variant_regression.py native/Fairy-Stockfish/src/stockfish
python3 tests/upstream_perft.py native/Fairy-Stockfish/src/stockfish
javac --release 8 -encoding UTF-8 -d build/classes app/src/org/janggilab/Engine.java app/src/org/janggilab/VariantConfig.java app/src/org/janggilab/AnalysisScore.java tests/EngineIntegration.java
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
