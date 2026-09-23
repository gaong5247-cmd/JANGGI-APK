# JanggiLab 변경 내역

## 엔진

- 엔진: Fairy-Stockfish Janggi variant
- 기준 엔진 커밋: `226c7f18c854372d5612be2a7d7f14449ae5a239`
- 장기 규칙은 Fairy-Stockfish의 `janggi` variant 구현을 사용한다.
- 장군, 실제 종국, 빅장, 반복, 한 수 쉼은 엔진의 현재 포지션 판정을 사용한다.
- UCI `score mate N`은 현재 외통이 아니라 탐색상 강제 승리/패배 예측으로만 표시한다.

## 앱 수정

- `appstate`, `appcheck`, `appresult`, `appreason`, `appbikjang`, `applegal`을 분리해 GUI가 검색 점수로 종국을 추측하지 않게 했다.
- Android에서 엔진 실행 시 앱의 native library 경로와 `/system/bin/linker64`를 사용하고 `libc++_shared.so`를 함께 찾는다.
- NNUE 파일 선택과 장기 NNUE 호환성 확인을 유지한다.
- 포지션 편집에서 장기판을 자유 배치할 수 있다.
  - 기물 터치 후 다른 칸 터치: 이동 또는 자리 교환
  - 기물 드래그: 이동 또는 자리 교환
  - 팔레트: 새 기물 배치/삭제
  - 왕·사 궁성 제한, 양쪽 왕 1개 검사, 기물 코드 검사를 적용한다.

## Termux ARM64 빌드

- 전체 Android SDK/NDK, Gradle, Android Studio 없이 빌드한다.
- Termux `clang++`, `javac`, `aapt2`, `d8`, `zipalign`, `apksigner`, `keytool` 사용
- Fairy-Stockfish ARM64 엔진은 `ARCH=armv8 COMP=clang OS=Android`로 빌드
- APK에 `lib/arm64-v8a/libfairy.so`와 `lib/arm64-v8a/libc++_shared.so` 포함
- 결과: `dist/JanggiLab-arm64-v8a.apk`

## 검증

- Fairy-Stockfish 엔진 smoke/perft/regression 테스트 통과
- 랜덤 장기 합법수 전이 및 appstate 회귀 테스트 추가
- APK는 Termux에서 `apksigner verify`와 `zipalign -c`로 검증한다.
