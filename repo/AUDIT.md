# 장기 판정 감사 및 수정 결과 — 2026-09-23

## 범위와 완료 상태

대상: `gaong5247-cmd/JANGGI-APK`, main `5d94bc2d98d7d1a1fe819d0e88c8fdab045ddf4c`.
엔진 기준: `ENGINE_REVISION`의 Fairy-Stockfish `226c7f18c854372d5612be2a7d7f14449ae5a239`.

소스 수정, Linux x86_64 엔진 실빌드, Java 브리지 컴파일/실행, 회귀 테스트는 완료했다.
**Android APK와 Windows EXE는 생성하지 못했다.** 로컬에 Android SDK/NDK 및 MinGW가 없고 SDK 다운로드는 프록시 연결 시간 초과였다. GitHub tree 생성도 `403 Resource not accessible by integration`으로 거부되어 원격 CI를 실행하지 못했다. 저장소 원격 파일·브랜치·main은 변경되지 않았다. 포함된 workflow 및 Android/Windows 빌드 절차는 실행 대기 상태다.

## 발견한 원인

| 문제 | 근거와 정확한 원인 | 수정 |
|---|---|---|
| 포획 후 왕이 갇히지 않았는데 “외통 1” | `MainActivity.info()`가 모든 `score mate N`을 `외통 N`으로 표시했다. 현재 국면과 탐색 예측을 혼동하는 표현이다. | 검색 평가를 `AnalysisScore`로 분리. `M5 · 강제 승리 예측`, `-M3 · 패배 예측`, M0도 단지 탐색 평가로 표시. |
| 외통이 아닌 규칙승도 mate 점수로 출력 | 이 fork의 `janggi`는 `JANGGI_MATERIAL`을 사용한다. `material_counting_result()`가 점수승을 `±VALUE_MATE`로 반환한다. 양측 한수쉼/빅장/반복/무진행 제한의 승패도 UCI `mate`로 직렬화될 수 있다. | `M`을 외통 또는 규칙승의 검색 예측으로 설명. PV나 점수에서 현재 종국 또는 정확한 미래 승리 원인을 추측하지 않는다. |
| 종국 이유를 구분할 수 없음 | `appstate`가 `appresult`와 `appcheck`만 제공했다. 손실 값만으로는 외통·연속 장군 위반·점수패를 구분할 수 없다. | 실제 판정이 발생하는 Position 분기에 선택적 reason 출력 인자를 추가했다. 검색 호출은 기존 기본 인자를 사용하므로 규칙/점수/우선순위는 보존한다. |
| 반복 종국 뒤 한수쉼 입력 가능 | 수정 전 반복 종국은 `appresult loss`인데 `applegal`에 32수가 남았다. `pass()`와 `commit()`에는 ongoing 가드가 없었다. | 종국의 앱용 playable 목록은 비운다. Java `canPlay()`와 UI 입력 가드가 종국 후 수 입력을 막는다. |
| 재빌드가 옛 엔진을 포함할 수 있음 | `scripts/build.py`가 엔진 복사 디렉터리가 이미 있으면 소스를 다시 복사하지 않았다. | 매 빌드에 소스를 갱신한다. |
| 배포 소스로 빌드 불가 | upstream Makefile이 `.gitignore`의 확장자 없는 파일 제외 규칙에 걸려 저장소에서 누락됐다. | 정확한 기록된 upstream 버전의 Makefile을 복구하고 ignore 예외를 추가했다. |

추가 방어: Java의 미완성 응답을 기본값 `ongoing`으로 허용하지 않는다. 필수 필드, 빈 합법수, 종국/원인 모순을 검증한다. 국면 갱신을 시작할 때 과거 분석 평가를 즉시 지우며, 기존 generation 검사를 유지한다. 빈 목록 파싱은 `applegal`과 `applegal ` 양쪽 모두 올바르게 처리한다. 이는 프로토콜 방어 강화이며, 정상 엔진이 빈 문자열 수를 보냈다는 재현 증거는 아니다.

**현재 보드의 합법수가 포획 직후 잘못 0이 되는 엔진 버그는 이번 검사에서 재현되지 않았다.** 확인되지 않은 핵심 엔진 오류를 추측해 이동 생성기를 바꾸지 않았다.

## upstream 대조

GitHub Git blob SHA를 기록된 upstream 커밋과 비교했다. `tests/upstream-comparison.json`에 SHA 근거가 있다.

- 수정 전 `position.cpp`, `position.h`, `movegen.cpp`, `variant.cpp`, `variants.ini`는 upstream과 동일했다.
- `uci.cpp`만 기존 앱의 `appstate` 확장 때문에 달랐다.
- 이번 변경은 `position.cpp/.h`의 종국 사유 노출과 `uci.cpp`의 앱용 응답 개선에 한정했다.
- `movegen.cpp`, `variant.cpp`, `variants.ini`, 공격/이동 규칙은 수정하지 않았다.

## appstate와 장기 규칙

`is_game_end()`는 immediate/optional 변형 종국을 판단하지만 일반적인 무합법수 외통/스테일메이트까지 모두 처리하는 함수가 아니다. 따라서 fallback 자체는 필요하다. 기존 순서, 즉 규칙 종국을 먼저 검사하고 아직 진행 중일 때만 LEGAL이 비었는지 확인하는 순서는 맞다. `MoveList<LEGAL>`는 immediate 종국 때문에도 비므로 이 순서를 바꾸면 빅장을 외통으로 오분류할 수 있다.

fallback은 하드코딩한 `-VALUE_MATE` 대신 upstream 검색과 같은 `pos.checkmate_value()` 또는 `pos.stalemate_value()`를 사용한다. 장기에서는 체크+합법수 없음이 실제 외통이며, LEGAL에는 허용되는 한수쉼도 포함된다.

| 규칙 | 감사한 구현/동작 | 고정 테스트 |
|---|---|---|
| 일반 한수쉼 | movegen이 왕의 같은 칸 이동을 SPECIAL로 생성하고 legal 필터를 통과한다. 왕 이동이 없더라도 pass만 있으면 진행 중이다. | start_position, pass_only_is_not_mate, one_pass_ongoing |
| 장군 중 한수쉼 | 일반 장군에서는 왕 칸이 여전히 공격받으므로 legal에서 제외된다. | rook_check_king_escape, cannon_check_with_screen |
| 빅장 예외 | upstream은 빅장 상태의 pass를 장군 중에도 허용한다. 빅장 제안 자체는 체크가 아니다. 다음 수에 제안 수락 또는 해소가 구별된다. | bikjang_offered_not_check, bikjang_declined, bikjang_check_pass_exception |
| 빅장/양측 pass 종국 | 동일 상태의 연속 bikjang 또는 연속 pass에서 종국. 현재 `janggi`는 점수 판정, `janggitraditional`은 무승부다. | accepted_material / accepted_traditional_draw / double_pass_* |
| 반복 | 전체 수순을 position moves로 전달해야 한다. FEN만으로 반복/이전 pass를 복원할 수 없다. 앱은 기존 전체 기보 전달을 유지한다. | one_repetition_ongoing, threefold_* |
| 연속 장군 | 실제 반복 판정 분기의 perpetualUs/perpetualThem 플래그에서 reason을 얻는다. 양측 연속 장군 및 점수 정책도 upstream 결과를 보존한다. | perpetual_check_penalty |
| 궁·사·차의 궁성 대각선 | mobilityRegion과 diagonalLines로 이동/공격 제한. 차 대각선의 중간 차단도 검사. | palace_king_boundary, palace_guard_boundary, palace_diagonal_rook_* |
| 포 | 한 개의 다리 필요, 포를 다리로 삼지 못하며 포를 잡지 못한다. 궁성 대각선에서도 같은 제한. | cannon_*, palace_diagonal_cannon_* |
| 마·상 | 마의 첫 직선 멱, 상의 두 경유 칸 차단을 검사한다. | horse_leg_*, elephant_* |
| 왕의 안전 | 공격 칸 진입, 보호된 공격 기물 포획은 제외. 보호되지 않은 공격 기물 포획은 허용. | king_cannot_enter_attacked_square, king_*checker |
| 장군 회피 | 왕 이동뿐 아니라 다른 기물의 공격자 포획·사이 막기도 생성한다. | check_capture_attacker, check_interposition |
| 실제 외통과 마지막 수비 | 왕의 탈출수 없음과 모든 회피수 없음을 함께 확인한다. | completed_checkmate, one_defence_survives, mate_in_one_* |
| 포획 뒤 상태 | 포획 뒤 왕 이동이 남아 있으면 ongoing. | ordinary_capture_after, capture_then_pass_mate_score_is_rule_win |

프로토콜 응답:

```text
appfen <현재 FEN>
applegal <앱에서 실제 진행 가능한 수들; 종국이면 빈 줄>
appresult ongoing|win|loss|draw
appreason none|checkmate|stalemate|bikjang|double_pass|repetition|perpetual_check|move_repetition|perpetual_chase|move_limit|board_full|variant
appcheck 0|1
appbikjang 0|1
appdone
```

승패는 **현재 둘 차례 기준**이다. `appbikjang`은 현재 왕 대면 상태, `appreason bikjang`은 실제 빅장 종국이다. 일반적인 다른 변형 종국의 정확한 이유를 추가로 분류하지 않았으면 `variant`를 사용한다. 검색 점수는 이 응답을 변경하지 않는다. `appstate`는 검색 중에 보내지 않는 기존 직렬 worker 계약을 유지한다. 선택적 종국을 앱에서 즉시 확정하는 정책도 기존과 동일하다.

## NNUE 설정 추가

설정 → **장기 NNUE 파일 선택** → Android 문서 선택기에서 파일 선택.

- SAF 파일을 앱 전용 저장소의 고유 `janggi-*.nnue` 이름으로 복사한다. upstream은 파일명 접두사로 variant 네트워크를 선택하므로 이 접두사를 유지해야 한다.
- 새 A/B 엔진에서 `EvalFile`, `Use NNUE=true`, 실제 depth 1 검색과 `NNUE evaluation ... enabled` 응답을 확인한 뒤 두 엔진을 교체한다.
- 빈 파일, 256 MB 초과 파일, 손상/호환 불가 네트워크는 거부한다. 실패한 새 프로세스를 닫고 기존 엔진/평가를 유지한다.
- 시작 시 저장한 파일이 없거나 손상되었다면 기본 평가로 복구한다. **기본 평가로 복구** 버튼도 제공한다.
- NNUE는 평가만 바꾼다. appresult/appreason과 합법수 판정은 바꾸지 않는다.
- `nnue=no` 빌드 옵션은 가중치 내장을 끄며, 런타임 외부 NNUE 기능은 남아 있다.
- 손상 파일 거부·기존 엔진 격리는 실엔진 Java 테스트로 확인했다. 정상 장기 가중치 파일은 제공받지 못했으므로 정상 가중치 로딩 및 Android 문서 선택 UI의 실기기 검증은 미완료다. 호환성 검증은 파일 구조/엔진 로딩 검증이지 훈련 데이터의 출처 증명은 아니다.

## 실제 실행 결과

상세 출력: `tests/audit-results/`.

| 검증 | 결과 |
|---|---|
| 수정 전 engine_smoke | 성공 — 기존 테스트만으로 UI 의미 오류를 잡지 못함 확인 |
| Linux x86_64 C++ 빌드 | 성공, LARGEBOARDS, 외부 NNUE 지원/내장 가중치 없음 |
| 수정 후 engine_smoke | 성공: 시작 32수, pass-only, 3개 perft, 60 ply 독립 엔진 대결, MultiPV/stop/isready |
| 장기 perft depth 4 | 1,065,277 / 76,763 / 151,202 — 수정 전후 동일 |
| 새 FEN/기보 fixture | 47개 성공 |
| 랜덤 국면 | seed 20260923, 120국면의 모든 4,515개 합법수 적용 성공, 포획 371건 포함 |
| Java 8 타깃 컴파일/통합 | 성공: 실제 mate 점수와 ongoing 공존, 실제 외통, 반복 종국, 응답 검증, NNUE 거부, 취소/재동기화 |
| upstream 대형 보드 perft | 기존 perft.sh의 largeboard 구간 33개 모두 성공. 로컬 expect 부재로 같은 원본 항목을 읽는 Python runner로 실행 |
| upstream Python 바인딩 | GCC로 소스 빌드 후 test.py 23개 성공 |
| stockfish bench | 성공, 6,257,668 nodes |
| variants.ini 파싱 | 종료 코드 0, 완료 |
| Android 전체 Java/APK 빌드 | 미실행: SDK/NDK 부재, 다운로드 연결 실패 |
| Windows x86_64 EXE | 미실행: MinGW 부재, GitHub 쓰기 권한 제한으로 CI 시작 불가 |
| Android 실기기 UI/NNUE·ARMv7/ARMv8 실행 | 미검증 |

앱은 APK의 `nativeLibraryDir/libfairy.so`를 직접 실행하지 않는다. 시작 시 `filesDir/fairy-engine`으로 복사하고 읽기·쓰기·실행 권한을 설정한 뒤 두 UCI 프로세스를 시작한다. Termux/aapt2가 ZIP Unix 권한을 잃어 엔진이 즉시 죽는 경우를 피하기 위한 조치다. 그래도 시작에 실패하면 화면에 `exit=<코드>, last='<엔진 마지막 출력>'`가 표시되어 ABI/링커 오류를 확인할 수 있다.

랜덤 검증은 각 수의 보드 변화, 왕 수, 차례 변경, 응답 필드, 부모 국면 복원 일치까지 검사한다. 탐색 전후 appstate가 완전히 동일한지도 fixture에서 검사한다. 모든 가능한 장기 국면에 대한 증명은 아니다.

## 대표 재현 명령

Linux 호스트 엔진 또는 빌드 후 Windows 엔진을 실행하고 다음 명령을 **단계별로** 넣는다. `go` 후 bestmove가 나오기 전에 quit/appstate를 보내지 않는다.

```text
uci
setoption name UCI_Variant value janggi
setoption name Use NNUE value false
isready
position fen 9/3k5/9/p8/9/9/R8/9/4K4/9 w - - 0 1 moves a4a7 d9d9
appstate
go depth 5
```

`a4a7`은 차가 졸을 잡는 수이고 `d9d9`는 상대의 한수쉼이다. 실제 출력은 `appcheck 0`, `appresult ongoing`, `appreason none`, 합법수 26개다. 검색은 `score mate 1 ... pv e2e2`를 낸다. 그 이유는 초가 한수쉼을 하면 양측 한수쉼으로 점수승하기 때문이다. 수정 전 UI는 이를 “외통 1”로 표시했다. 수정 후 UI는 “M1 · 강제 승리 예측”으로 표시하고 실제 상태는 계속 진행 중이다.

실제 외통 비교:

```text
position fen 9/3k5/9/9/9/9/9/3rrr3/9/4K4 w - - 0 1
appstate
```

기대: `appcheck 1`, `appresult loss`, `appreason checkmate`, 빈 applegal.

반복 종국 비교:

```text
position startpos moves b1c3 b10c8 c3b1 c8b10 b1c3 b10c8 c3b1 c8b10
appstate
```

현재 janggi 점수 규칙에서는 `loss/repetition`, 빈 applegal이다. 수정 전에는 같은 종국에 32개 수가 남아 있었다. `janggitraditional`로 실행하면 무승부지만 앱 기본 규칙은 바꾸지 않았다.

## 변경 파일

- `app/src/org/janggilab/MainActivity.java`: UI 상태 분리, 종국 입력 가드, NNUE 설정/파일 가져오기/엔진 교체.
- `app/src/org/janggilab/Engine.java`: 필수 상태 파싱, reason/bikjang/canPlay, NNUE 사전 검증, 초기화 실패 프로세스 정리, exit code/마지막 출력 진단.
- `app/src/org/janggilab/AnalysisScore.java`: 탐색 점수 전용 표시 및 종국 원인 한국어 표현.
- `native/Fairy-Stockfish/src/uci.cpp`, `position.cpp`, `position.h`: 판정 분기의 원인 노출과 appstate.
- `native/Fairy-Stockfish/src/Makefile`, 엔진 `.gitignore`: upstream 빌드 파일 복원.
- `tests/janggi_mate_cases.json`, `janggi_regression.py`, `EngineIntegration.java`, `engine_smoke.py`, `upstream_perft.py`: 회귀/기존 테스트.
- `tests/upstream-comparison.json`, `tests/audit-results/*`: 비교 근거와 실행 로그.
- `scripts/build.py`, `scripts/package.py`, `scripts/package-termux.py`, `tests/verify_apks.py`, `.github/workflows/build.yml`: ABI별/Universal APK, Windows 엔진 EXE 빌드와 검증 작업.
- `app/AndroidManifest.xml`: 버전 1.1.0 / code 2.
- `README.md`, `AUDIT.md`, 루트 `.gitignore`: 사용법·감사 결과·빌드 산출물 제외.

Windows 산출물은 **UCI 장기 엔진 EXE**이며 별도 Windows GUI 앱이 아니다. 기존 프로젝트가 Android GUI이므로 임의로 Windows GUI를 새로 만들지 않았다.
