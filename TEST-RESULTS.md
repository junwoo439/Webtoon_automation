# 0.1.2 검증 결과

- 핵심 로직 검사 81개, 입력 판정 검사 17개, 스크롤 끝 후보 판정 검사 32개: 총 130개 통과.
- 회귀 시나리오: 유사한 여백 20화면에 이동 이벤트가 있으면 계속 진행, 두 장만으로 정지하지 않음, 이동 불가 시 제한된 재시도, 글자가 0~180자일 때 배속에 따른 대기시간 차이, 배속에 따른 스와이프 시간 차이.
- Gradle 8.11.1 / AGP 8.9.2 / JDK 21.0.13 / SDK 35로 최종 :app:assembleDebug 및 :app:lintDebug 성공.
- APK v2 서명 검증 성공. 0.1.1과 인증서 SHA-256 일치 확인.
- 최종 APK에 INTERNET/ACCESS_NETWORK_STATE 권한 없음. 접근성 권한/스크린샷/제스처 기능은 서비스 설정으로 활성화해야 함.
- 실기기 및 에뮬레이터 UI 테스트는 여기서 실행하지 못함. 카카오페이지 실제 호환성, 드래그 및 메뉴 위치, 입력 이벤트 수신, 정지 버튼은 휴대폰에서 확인 필요.
- Lint 오류 0개, 경고 6개. ClickableViewAccessibility는 메뉴의 사용자 터치 처리에 대한 경고이며, 탭 완료 시 performClick을 호출한다. RtlHardcoded는 드래그 위치를 화면 절대 좌표로 계산하는 LEFT gravity에 대한 경고이다.

## Lint 경고

- UseSwitchCompatOrMaterialCode: Use `SwitchCompat` from AppCompat or `SwitchMaterial` from Material library
- DataExtractionRules: The attribute `android:allowBackup` is deprecated from Android 12 and higher and may be removed in future versions. Consider adding the attribute `android:dataExtractionRules` specifying an `@xml` resource which configures cloud backups and device transfers on Android 12 and higher.
- StaticFieldLeak: Do not place Android context classes in static fields (static reference to `ReaderService` which has field `overlay` pointing to `LinearLayout`); this is a memory leak
- UnusedResources: The resource `R.string.app_name` appears to be unused
- ClickableViewAccessibility: Custom view ``Button`` has `setOnTouchListener` called on it but does not override `performClick`
- RtlHardcoded: Use "`Gravity.START`" instead of "`Gravity.LEFT`" to ensure correct behavior in right-to-left locales
