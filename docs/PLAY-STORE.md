# Google Play 등록 준비

작성일: 2026-10-26. 이 환경에서는 Google 정책 사이트 접속이 차단되어 아래 공식 문서의 최신 내용을 직접 조회하지 못했습니다. 계정별 테스트 요건, target API, 접근성 정책과 제출 양식은 제출 시 Play Console에서 다시 확인해야 합니다. 현재 빌드는 등록 승인을 받은 앱이 아닙니다.

## 먼저 할 일

1. [Play Console](https://play.google.com/console/signup)에서 본인 또는 사업자 명의 개발자 계정을 만들고 신원·연락처 확인을 완료합니다. 일반적으로 가입비는 미화 25달러 1회이며, 실제 결제 화면의 금액을 확인하세요. 조직 계정은 조직 검증/D-U-N-S 등의 추가 정보가 요구될 수 있습니다.
2. 앱 이름, 개발자 연락처, 개인정보처리방침의 공개 HTTPS 주소를 준비합니다. 이 저장소의 `privacy-policy-draft.md`는 운영자 정보를 채우기 전의 초안입니다.
3. 실기기에서 무료 회차, 결제 화면, 터치 정지, 앱 전환, 화면 회전 등을 검증합니다. 현재 카카오페이지 인식 변경은 실기기 확인이 남았습니다.
4. 업로드 키를 본인 관리 위치에 생성·백업하고 Play App Signing을 설정합니다. 현재 APK는 테스트 서명이므로 출시 파일로 사용하지 않습니다.
5. 서명된 Android App Bundle(AAB)을 먼저 내부 테스트 트랙에 올립니다. Console의 대상 API·기기 호환성·SDK 경고를 해결합니다.
6. 해당되는 비공개 테스트를 완료하고 프로덕션 액세스를 신청합니다. 2023-11-13 이후 생성된 새 개인 계정에는 일반적으로 **12명 이상이 연속 14일 참여하는 비공개 테스트** 요건이 적용되어 왔습니다. 테스트 완료가 자동 출시 승인을 뜻하지는 않으며, 실제 계정 화면에 표시된 기준을 우선합니다.
7. 스토어 등록정보, 앱 콘텐츠 설문, 접근성 선언 및 심사 영상을 제출한 뒤 프로덕션 심사를 요청합니다.

공식 안내: [계정 등록](https://support.google.com/googleplay/android-developer/answer/6112435?hl=ko), [새 개인 계정 테스트](https://support.google.com/googleplay/android-developer/answer/14151465?hl=ko), [앱 서명](https://support.google.com/googleplay/android-developer/answer/9842756?hl=ko).

## 이 앱에서 특히 확인할 접근성 선언

툰페이스는 다른 앱의 화면 글자·버튼을 읽고 스크린샷으로 OCR을 수행하며 제스처를 실행합니다. 따라서 [AccessibilityService API 정책](https://support.google.com/googleplay/android-developer/answer/10964491?hl=ko)의 검토가 필요합니다.

- 현재 목적은 일반 사용자의 읽기 보조입니다. 장애인 지원이 주된 목적이라고 사실과 다르게 신고하거나 `isAccessibilityTool=true`를 추가하지 않습니다.
- 현재 `isAccessibilityTool`을 선언하지 않았습니다. 앱 안에서 권한 사용 목적·데이터 처리·철회 방법을 먼저 설명하고 명시적으로 동의를 받습니다. Console의 접근성 사용 선언과 시연 영상을 준비합니다.
- 사용자가 선택한 앱에서 ▶를 누른 뒤에만 시작하고, 다음 화 자동 이동은 별도 설정으로 켭니다. 탐색 조건과 결제·로그인·광고 시청 중단 조건을 설명해야 합니다.
- 접근성 API의 자율 동작 관련 제한을 제출 시 확인합니다. 현재 동작은 사용자가 시작하는 제한된 규칙 기반 흐름입니다. 향후 모델을 학습하더라도 결제, 광고 클릭, 임의 앱 조작을 모델이 결정하도록 확장하지 않습니다. 이 설계만으로 심사 통과를 보장할 수는 없습니다.
- 화면·대사·설치 앱 목록·접근성 데이터를 광고 타기팅/광고 SDK에 전달하지 않습니다. 타 앱 위에 광고를 띄우지 않습니다.
- 카카오페이지 등 타 서비스의 이용약관도 별도로 확인합니다. 권한 동의나 Play 승인이 타 서비스의 허락을 대신하지 않습니다.

심사 영상에는 첫 실행 → 동의 → 접근성 활성화 → 앱 선택 → 무료 회차에서 시작/속도 변경 → 터치 정지 → 선택적 다음 화 이동 → 동의 철회를 실제 기기로 보여 주세요. 자동 결제 해제를 안내하고, 실제 검증한 범위만 설명합니다.

## AAB 빌드와 업로드 서명

현재 `compileSdk/targetSdk`는 **35**, `minSdk`는 **30**입니다. [제출 시 대상 API 요건](https://support.google.com/googleplay/android-developer/answer/11926878?hl=ko)을 확인하고 필요하면 SDK/AGP와 targetSdk를 올린 뒤 동작을 다시 검증하세요. 현재 targetSdk가 제출일의 요건을 충족한다고 확인된 상태는 아닙니다.

아래 네 환경변수가 **모두** 설정되면 release 빌드에 업로드 서명을 적용합니다. 키 파일·비밀번호를 Git에 넣지 마세요.

| 환경변수 | 값 |
| --- | --- |
| `TOONPACE_UPLOAD_STORE_FILE` | 본인이 관리하는 업로드 keystore의 절대 경로 |
| `TOONPACE_UPLOAD_STORE_PASSWORD` | keystore 비밀번호 |
| `TOONPACE_UPLOAD_KEY_ALIAS` | 업로드 키 alias |
| `TOONPACE_UPLOAD_KEY_PASSWORD` | 업로드 키 비밀번호 |

Android Studio 또는 로컬 터미널에서 `./gradlew :app:bundleRelease`를 실행합니다. 결과는 `app/build/outputs/bundle/release/app-release.aab`입니다. 네 변수가 모두 없으면 **미서명** 빌드가 생성됩니다. 한두 개만 설정하면 혼동을 막기 위해 빌드가 실패합니다. 업로드 전 `jarsigner -verify -verbose -certs`와 Console에서 서명 상태를 확인하세요.

Play 앱 서명과 기존 테스트 APK 서명이 다르면 기존 테스트 앱 위에 바로 업데이트할 수 없습니다. 테스트 설정을 기록하고 기존 APK를 제거한 뒤 Play 테스트 버전을 설치해야 할 수 있습니다. 키가 정해진 이후에는 동일한 앱 서명을 유지합니다.

현재 포함된 ML Kit 네이티브 라이브러리의 16 KB 정렬 여부는 `TEST-RESULTS.md`에 기록합니다. 정렬 검사 통과는 16 KB 실기기 전체 실행 검증을 대신하지 않습니다. [16 KB 페이지 크기 안내](https://developer.android.com/guide/practices/page-sizes).

## 스토어 자료와 설문

- 512×512 앱 아이콘, 1024×500 피처 그래픽, 실제 앱 스크린샷과 짧은 소개/상세 설명을 준비합니다. 타 서비스 작품 그림은 배포 권한이 없으면 스토어 홍보물에 사용하지 않습니다.
- 현재 이름 후보: **툰페이스 — 읽기 속도 도우미**.
- 짧은 소개 초안: **선택한 앱에서 읽는 속도에 맞춰 스크롤하고, 터치로 잠시 멈추세요.**
- 상세 설명에는 접근성 권한 필요 이유, 기기 내 OCR 처리, 지원 Android 버전, 다음 화 인식의 제한을 명시합니다. 실제 검증되지 않은 앱을 완전 지원한다고 표시하지 않습니다.
- 콘텐츠 등급, 타깃 연령, 광고 포함 여부, 앱 액세스 안내, [데이터 보안](https://support.google.com/googleplay/android-developer/answer/10787469?hl=ko)을 작성합니다.
- 현재 배너는 자리만 있고 광고 SDK/전송이 없습니다. 실제 광고 SDK를 넣은 버전에서는 광고 포함 여부·데이터 보안·개인정보처리방침을 함께 갱신합니다.

## 광고를 켤 때

`BannerSlot`은 툰페이스 **MainActivity의 하단**에 있고 스크롤 콘텐츠와 별도의 높이를 사용합니다. 웹툰 위의 접근성 조작부에는 광고가 없습니다. debug 빌드는 ‘광고 영역 미리보기’를 표시하고 release 빌드는 광고 공급자가 연결되기 전까지 영역을 숨깁니다.

1. AdMob 계정을 만들고 앱 및 적응형 배너 광고 단위를 등록합니다. 현재는 실제 AdMob ID, 광고 SDK, 광고 요청이 없습니다.
2. [공식 Android 배너 가이드](https://developers.google.com/admob/android/banner)에 따라 현재 지원 SDK를 추가하고 `BannerSlot.Provider`로 구현합니다. 배너 폭은 실제 슬롯 폭에서 구하고, Activity의 resume/pause/destroy에 맞춰 AdView를 관리합니다. 테스트 중에는 공식 테스트 ID만 사용합니다.
3. 필요한 지역에서 [UMP 동의](https://developers.google.com/admob/android/privacy)를 받고 광고 요청 가능 상태가 된 후에만 슬롯에 붙입니다. 미동의/오류/no-fill일 때는 숨기고, 요구되는 경우 개인정보 선택 UI를 제공합니다.
4. 현재 manifest는 INTERNET/ACCESS_NETWORK_STATE를 명시적으로 제거합니다. 광고 연결 시에만 이 정책을 수정하고, 추가되는 광고 식별자 권한과 SDK 데이터 수집을 확인합니다. 화면 인식 서비스에는 광고 SDK를 연결하지 않습니다.
5. 광고 포함 설문, 개인정보처리방침, 데이터 보안, 필요 시 app-ads.txt를 갱신합니다. 본인 광고를 클릭하거나 접근성 기능으로 광고 노출·클릭을 자동화하지 않습니다.

실제 광고 송출은 이 준비 작업과 계정·동의 설정을 완료한 다음 별도 버전에서 활성화합니다.
