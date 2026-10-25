# 검증 결과 — 2026-10-26 (한국 시간)

최신 src.zip 및 업로드한 빌드 설정을 기준으로 수정했습니다.

- 기존 핵심 로직 검사 73개 통과.
- 중단 판정 회귀 검사 17개 통과. 자동 스크롤 뒤 관성 이벤트, 지연 도착 이벤트, 대기 중 클릭/수동 스크롤, 다음 화 대기 중 입력, 이전 실행 이벤트를 확인.
- Gradle 8.11.1, AGP 8.9.2, JDK 21.0.13, Android SDK 35 / Build-Tools 35.0.0으로 :app:assembleDebug 및 :app:lintDebug 성공.
- apksigner verify 성공: APK Signature Scheme v2, Android Debug 테스트 서명.
- 최종 APK 요청 권한은 app.toonpace.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION 하나. INTERNET 및 ACCESS_NETWORK_STATE 없음. 접근성 서비스 권한/기능은 별도 서비스 선언에 존재하며 사용자 설정으로 활성화해야 함.
- Android 에뮬레이터/실기기 실행은 여기서 하지 않았음. 실제 입력 이벤트 전달, OCR, 연속 스크롤 및 정지 버튼은 사용자 기기에서 재확인 필요.
- Play 프로텍트 차단 원인 및 차단 해제 여부는 미확인. 테스트 서명은 정식 배포용 개인 서명 키가 아님.
- Windows 스크립트 변경은 소스 확인만 했으며 Windows에서 실행하지 않았음. SDK Manager GUI 설치 후 CLI를 호출하지 않고 진행하도록 변경.

## Lint

- UseSwitchCompatOrMaterialCode [Warning]: Use `SwitchCompat` from AppCompat or `SwitchMaterial` from Material library
- DataExtractionRules [Warning]: The attribute `android:allowBackup` is deprecated from Android 12 and higher and may be removed in future versions. Consider adding the attribute `android:dataExtractionRules` specifying an `@xml` resource which configures cloud backups and device transfers on Android 12 and higher.
- StaticFieldLeak [Warning]: Do not place Android context classes in static fields (static reference to `ReaderService` which has field `overlay` pointing to `LinearLayout`); this is a memory leak
- UnusedResources [Warning]: The resource `R.string.app_name` appears to be unused

## APK SHA-256

`5d5faef5030e891242b005a4e9bdaa0f6a9f4b8e2051da69b6f818784f192f86`
