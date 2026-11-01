# #25 Android WebView 셸 기초

- 이슈: [#25](https://github.com/yim0327/beone-mvp/issues/25)
- 브랜치: `feat/25-android-webview-shell` (기준 `origin/develop` `cbc50fb`, #22와 독립)
- 기술 설명: [Android WebView 셸](../../android/webview-shell.md)
- 진행 기록 README는 #22 PR(#24)에 있습니다. 같은 파일을 두 PR이 새로 만들면 충돌하므로, #24가 병합된 뒤 이 작업의 줄을 반영합니다.

### 2026-10-28 20:05 · 신뢰 origin 판정 · 완료
- 한 일: 주소의 스킴·호스트·포트를 비교해 WebView 안에서 열어도 되는지 판정하는 기능을 만들었습니다.
- 서비스에서 달라진 점: 설정된 시작 주소와 같은 origin만 열 수 있고, 설정이 없으면 아무 주소도 열지 않습니다(화면 연결 전).
- 검증: `./gradlew testDebugUnitTest` 실제 실행, 통과(기준 `cbc50fb` + 미커밋 변경)
- 다음: WebView 화면 연결

### 2026-10-28 20:12 · WebView 셸과 debug/release 분리 · 완료
- 한 일: 앱 첫 화면을 WebView로 바꾸고, debug는 개발 서버를, release는 아무것도 열지 않도록 나눴습니다.
- 서비스에서 달라진 점: debug 앱이 `http://localhost:5173/`의 React 개발 화면을 엽니다. release 앱은 "웹 화면 주소가 설정되지 않았습니다"를 보여줍니다. 원격 디버깅과 평문 HTTP는 debug의 로컬 주소에서만 됩니다.
- 검증: `assembleDebug testDebugUnitTest lintDebug assembleRelease lintRelease` 실제 실행, 통과. release의 `BuildConfig`·네트워크 보안 설정·dex에 개발 서버 주소가 없음을 확인(기준 `cbc50fb` + 미커밋 변경)
- 참고: AGP 9 기본 설정에는 release 단위 테스트 태스크가 없어, release 값은 빌드 산출물로 확인했습니다.
- 다음: 내부 리뷰

### 2026-10-28 20:20 · 내부 리뷰 반영 · 완료
- 한 일: 읽기 전용 하위 에이전트의 내부 리뷰(독립 리뷰 아님) 지적을 확인하고 고쳤습니다.
- 서비스에서 달라진 점: POST 폼 제출로 다른 origin에 가는 경우도 막습니다. 키보드가 입력란을 가리지 않습니다. 첫 화면에서 뒤로 가기 후 돌아와도 웹 뒤로 가기가 동작합니다.
- 반영하지 않은 의견: iframe·하위 리소스 origin 제한과 `about:blank`·`blob:` 처리는 브리지·화면 요구가 정해질 때 판단하도록 문서에 남겼습니다.
- 검증: 위 5개 태스크 실제 실행, 통과, lint 경고 8건 모두 기존 항목. `-Pbeone.devServerUrl`에 따옴표가 있어도 빌드됨을 확인(기준 `cbc50fb` + 미커밋 변경)
- 남은 제한: 실기기·계측 테스트 미실행. release 로딩 방식·신뢰 origin·브리지는 사용자 결정 대기
- 다음: 커밋, PR

### 2026-10-31 18:00 · 최신 develop 반영과 설정 회귀 테스트 · 완료
- 한 일: 최신 `develop`(`06ca2b8`)을 이 브랜치에 merge했습니다(충돌 없음, `android/` 변경 없음). 네트워크 보안 설정 두 개와 Manifest 연결을 소스에서 읽어 확인하는 단위 테스트를 더했습니다. main은 평문 HTTP를 어디에도 허용하지 않고, debug는 `localhost`·`127.0.0.1`·`10.0.2.2`만 하위 도메인 없이 허용하며, Manifest에 전역 평문 허용·`debuggable`이 없다는 것입니다. http 기본 포트(80) 생략 비교도 추가했습니다.
- 서비스에서 달라진 점: 없음(테스트만 추가).
- 검증: `testDebugUnitTest --rerun-tasks` 실제 실행, 통과. 단위 테스트 15건(XML 집계, 기존 12건 + 신규 3건), 실패·skip 0(기준 `fa423f3` + 미커밋 테스트 변경)
- 남은 제한: 소스 설정 검사이며 기기에서 실제 연결이 막히는지는 확인하지 않았습니다. 실기기·계측 테스트는 미실행입니다.
- 다음: 실기기 확인 절차 정리, 모듈 전체 검증, PR 본문 갱신

### 2026-10-31 18:20 · 실기기 확인 절차·release 정적 점검·PR 갱신 · 완료(실기기 미실행)
- 한 일: W1~W5를 사용자가 바로 시험할 수 있게 설치·APK 대조 절차와 항목별 증거를 [기술 설명](../../android/webview-shell.md#debug-실기기-관측-절차-2026-11-01-준비-사용자-관측-대기)에 정리했습니다. release APK의 Manifest·네트워크 보안 설정·dex를 점검했고, `about:blank`·`blob:` 차단 테스트를 더했습니다. PR 본문 검증 칸을 최신 head 기준으로 갱신했습니다.
- 서비스에서 달라진 점: 없음(문서·테스트만).
- 검증: `assembleDebug testDebugUnitTest lintDebug assembleRelease lintRelease --rerun-tasks` 실제 실행, 통과(단위 테스트 16건 XML 집계, lint 오류 0·경고 7). release에 `debuggable`·평문 허용·개발 서버 주소·앱 로그 호출이 없음을 확인했습니다. debug·release APK는 `844841e` 때와 같은 바이트입니다.
- 남은 제한: 실기기·실제 React 화면·계측 테스트 미실행. W2 판정 기준, W3 계열 실행 방식, release 시험용 서명은 사용자 결정 대기. 독립 리뷰는 과금 여부가 불명확해 요청하지 않음(리뷰 대기)
- 다음: 결정·기기 승인 뒤 실기기 확인, release 로딩 방식 결정 뒤 구현

### 2026-11-01 17:00 · debug 실기기 관측 준비 · 완료(사용자 관측 대기)
- 한 일: 최신 `develop`(`d9ae633`)을 merge했습니다(충돌 없음, `android/` 변경 없음). debug·release를 다시 빌드·검증하고, 사용자가 W1·W2·W5(debug)를 직접 관측할 수 있게 [관측 절차](../../android/webview-shell.md#debug-실기기-관측-절차-2026-11-01-준비-사용자-관측-대기)를 고쳐 썼습니다. 설치 전 확인 명령, 기기 인증 앱을 덮어쓰는 영향과 되돌리는 방법, 설치본 sha256 대조, 관측표·회신 양식이 들어 있습니다. 2026-10-31 절차는 이 절차로 바꿨고, 위 기록의 링크도 새 절차를 가리키게 고쳤습니다.
- 서비스에서 달라진 점: 없음(문서만).
- 검증: `assembleDebug testDebugUnitTest lintDebug`, `assembleRelease lintRelease`를 `--rerun-tasks`로 실제 실행, 통과(단위 테스트 16건 XML 집계, lint 오류 0·경고 7). 기준은 merge 커밋 `16f5a7e`이며 문서 변경은 그 뒤입니다. debug APK sha256 `0af9497b…`는 이전과 같습니다.
- 남은 제한: 기기 설치·관측은 사용자 대기입니다. W2는 판정 기준이 미결이라 관측값만 기록하고, W3·W3a·W3b·W4·W5(release)는 미실행입니다.
- 다음: 사용자 관측 결과 기록
