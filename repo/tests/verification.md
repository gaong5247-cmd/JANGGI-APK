# 검증 기록

- Fairy-Stockfish commit: `226c7f18c854372d5612be2a7d7f14449ae5a239`
- 장기 perft 깊이 4: 1,065,277 / 76,763 / 151,202. Upstream 기대값 일치.
- 초기 합법수 32개, 한 수 쉼과 10번째 줄 좌표 처리 확인.
- 네이티브 엔진 2개 60수 합법 진행.
- 실제 앱 Java Engine 클래스로 별도 20수 대결, 분석 중단, 응답 동기화, 취소된 요청 차단 확인.
- 원본 엔진 bench / variants.ini 검증 성공.
- Android ARM64와 x86_64 네이티브 바이너리 빌드.
- ARM64 ELF LOAD 정렬 0x4000, Android 16KB 페이지 대응.
- APK v2/v3 서명 검증 성공. minSDK 26, targetSDK 35.
- 네트워크 권한 없음.

실제 ARM64 휴대폰 실행은 이 환경에서 검증하지 않았습니다.

Android 에뮬레이터는 하드웨어 가속 없이 부팅을 시도했으나 시스템 패키지 서비스가 준비되지 않았습니다. 설치 시 `Cannot find service: package` 및 미초기화 PackageManagerInternal 오류가 발생해 APK 설치·화면 실행 검증을 완료하지 못했습니다. 앱 실행 오류로 판정한 결과는 아닙니다.
