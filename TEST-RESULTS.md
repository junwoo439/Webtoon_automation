# ToonPace 0.1.4 검증 기록

2026-10-26, 클라우드 Linux 환경. 실제 휴대폰/카카오페이지 실행 검증은 포함하지 않습니다.

## 실행 결과

- `sh test-core.sh`: **222개 검사 통과** — 속도·문구 84, 입력 구분 19, 스크롤 끝 47, 다음 회차 후보·화면 구조 72.
- `:app:assembleDebug :app:lintDebug :app:bundleRelease`: 최종 **BUILD SUCCESSFUL**, 40초, 94개 작업 중 23개 실행·71개 up-to-date.
- Lint: 오류 0, 경고 7. 기존 Switch 사용, dataExtractionRules, 서비스 정적 참조, 미사용 리소스, 터치 리스너 접근성 2건, LEFT 정렬 관련 경고가 남았습니다.
- APK: package `app.toonpace`, versionName `0.1.4`, versionCode `5`, 52,565,699 bytes.
- APK 서명 검증 통과. 이전 테스트 APK와 동일한 Android Debug 인증서.
- 인증서 SHA-256: `aa5baa7e67538546d902507c01fd19d3cbbf764fedb15cc32420e802ec646625`.
- AAB: `app/build/outputs/bundle/release/app-release.aab`, 21,584,826 bytes. 업로드 키를 설정하지 않아 **미서명**이며 Play 제출용 최종 파일이 아닙니다. `jarsigner -verify`에서 unsigned 확인.
- 최종 APK/AAB ZIP 무결성 확인.
- APK `zipalign -c -P 16 4` 통과. APK/AAB의 arm64-v8a, x86_64 ML Kit 라이브러리 LOAD 세그먼트는 모두 16 KB 정렬. armeabi-v7a, x86 32비트 라이브러리는 4 KB 정렬이며 별도로 구분했습니다. 16 KB Android 실기기 실행은 미검증.
- APK의 uses-permission은 앱 내부 signature 수준 receiver 권한뿐입니다. INTERNET/ACCESS_NETWORK_STATE를 추가하지 않았고 실제 광고 SDK도 없습니다. 접근성 서비스 권한 선언은 별도로 유지됩니다.

## 회귀 검사 범위

- 사용자가 제공한 두 작품의 `다음화 무료로 보기` / `다음화 보기` 문구와 카드·추천 제목 위치를 사람이 읽어 옮긴 사례.
- 다음 회차 카드 아래의 `이 작품과 함께보는 웹툰` 제목으로 회차 종료 판단. 추천 목록의 물리적인 끝까지 내리지 않음.
- 글자 크기/화면 크기 변화, 줄이 분리된 문구, 같은 후보의 OCR/접근성 중복, 서로 다른 후보의 모호함, 화면 상단 쪽으로 이동한 하단 카드.
- 원작소설 링크·추천 화살표·이전 회차·결제/광고 문구 제외.
- 스크롤 최대 위치가 반복 확인될 때 중앙 애니메이션과 구별. 추가 콘텐츠가 로딩되면 종료 근거 취소.

이 검사는 ML Kit를 제공된 이미지 픽셀에 실행한 결과가 아니며 OCR 정확도, 이미지 모델 학습 결과 또는 실기기 성공률을 뜻하지 않습니다. 새 딥러닝 모델은 학습하지 않았습니다. 사전 학습된 ML Kit OCR 뒤의 판별 로직을 수정했습니다.

## 아직 확인할 항목

- 카카오페이지 실제 Android UI의 접근성 노드, OCR 출력, 다음 회차 열림, 터치 정지 및 화면 회전.
- 실제 광고 어댑터, AdMob/UMP, 광고 동의/no-fill/실제 수익. 현재는 Activity 하단 슬롯과 debug 미리보기만 구현했습니다.
- 실제 업로드 키를 사용한 AAB 서명, Play Console 업로드·심사, 계정별 테스트 요건 및 제출일 target API 충족. 현재 targetSdk 35.
- 공식 Google 정책 사이트는 이 환경의 네트워크 정책으로 접속이 차단되어 최신 내용을 조회하지 못했습니다. docs/PLAY-STORE.md의 공식 링크와 Console에서 재확인해야 합니다.
