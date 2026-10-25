# 0.1.3 검증 결과

- 핵심 로직 검사 84개, 입력 판정 검사 19개, 끝 후보 판정 검사 43개: 총 146개 통과.
- 5배 대기/스와이프 시간이 이전 최대 2배보다 짧아지는 것, 빠른 속도의 안정화 대기, 계속 움직일 때 추가 대기, 최대 위치 도달 판단, 끝에서의 불필요한 이동량 이벤트, 지연 로딩으로 최대 위치가 바뀌는 경우, 비슷한 여백에서 회차를 건너뛰지 않는 것을 확인.
- 최종 :app:assembleDebug 및 :app:lintDebug 성공. Gradle 8.11.1 / AGP 8.9.2 / JDK 21.0.13 / SDK 35.
- APK 서명 검증 통과. 이전 0.1.2와 동일한 인증서로 서명되어 업데이트 가능. versionCode 4, versionName 0.1.3.
- 최종 APK에 INTERNET 권한 없음.
- 실기기/에뮬레이터 실행은 여기서 하지 못함. 새 투명 터치 영역의 터치 수신, 조작부보다 아래에 배치되는지, 정지 후 정상 조작, 자동 제스처 주입 중의 입력, 카카오페이지의 실제 다음 화 버튼 인식/이동은 휴대폰에서 확인 필요.
- 다음 화 기능은 버튼 문구/위치 및 끝 판정에 의존하며, 결제/로그인 차단 조건은 유지됨. 실제 문제 화면의 버튼 문구는 아직 제공되지 않았음.
- 앱의 첫 터치는 일시정지에 사용됨. 자동 제스처 주입 중에는 터치 영역을 일시적으로 숨기므로 항상 모든 터치를 잡는다고 보장하지 않음. Ⅱ 버튼 유지.

## Lint 경고 (7개)

- UseSwitchCompatOrMaterialCode: Use `SwitchCompat` from AppCompat or `SwitchMaterial` from Material library
- DataExtractionRules: The attribute `android:allowBackup` is deprecated from Android 12 and higher and may be removed in future versions. Consider adding the attribute `android:dataExtractionRules` specifying an `@xml` resource which configures cloud backups and device transfers on Android 12 and higher.
- StaticFieldLeak: Do not place Android context classes in static fields (static reference to `ReaderService` which has field `overlay` pointing to `LinearLayout`); this is a memory leak
- UnusedResources: The resource `R.string.app_name` appears to be unused
- ClickableViewAccessibility: Custom view ``Button`` has `setOnTouchListener` called on it but does not override `performClick`
- ClickableViewAccessibility: `onTouch` lambda should call `View#performClick` when a click is detected
- RtlHardcoded: Use "`Gravity.START`" instead of "`Gravity.LEFT`" to ensure correct behavior in right-to-left locales
