# Android 진행 상황

- 작성: 2026-10-31, 기준 `origin/develop` `06ca2b8`(세 작업 브랜치 모두 이 커밋을 merge함). #22 실기기 요약은 2026-11-04, #22 병합과 #25·#27 상태는 2026-11-05 갱신, #25의 PR #26 병합 상태와 임시 fixture 실기기 관측 결과는 2026-11-06 갱신, #73의 PR #74 병합 상태는 2026-11-14 갱신(문서 기준 `origin/develop` `f751c78`. 문서 전체를 이 날짜로 다시 검증하거나 테스트를 다시 실행한 것은 아님)
- 상태 구분: #22는 PR #24로, #27은 PR #72로, #25는 PR #26으로(squash 병합 커밋 `5a0065f6f7f86f655cb417ccca3db07817737e09`), #73(임시 관측 fixture)은 PR #74로(`f751c78434d7ef52fdb38d36e81554898222c7f8`) `develop`에 반영됐습니다. 병합이 실기기 전체 검증·release·브리지·업무 API 완료를 뜻하지는 않습니다.

## 지금 가능한 것

- 네이티브 기기 인증(#22): 지문 또는 기기 PIN을 한 인증 창으로 받습니다(D-08). 인증 수단이 없으면 활성화하지 않고 설정 안내를 합니다.
- 60초 활성 상태(#22): 성공 시 60초 활성, 만료·실패·취소·앱 이탈 시 즉시 비활성, 활성 중 재인증 성공 시 새 60초. 회전 등 화면 구성 변경은 이탈로 보지 않습니다.
- 인증 요청 하나만 진행(#22, **검증용 구현**): 앱 이탈로 취소한 요청은 `정리 중`으로 두고 최종 결과가 올 때까지 새 요청을 받지 않습니다. 응답이 없으면 `정지`로 바꾸고 활성화하지 않습니다. 응답 대기 시간 현재 기본값은 120초·10초입니다(근거 없는 제안, D-21). 30초·5초는 2026-10-29~30 관측에 쓴 이전 실험값입니다.
- 확인 수단(#22): 단위 테스트 84건(가짜 authenticator, 2026-10-31 실행), debug 앱의 **B:ONE 인증 점검** 화면과 debug 전용 관측 로그 `BeoneAuthTrace`(release 미포함, release APK 정적 점검으로 확인)
- 실기기(#22, SM-S942N Android 17 한 대): 구조 도입 전 코드로 1~15번 통과. 지금 코드로 R1~R4·R6~R9와 1~6·8~11·13번 관측상 통과, R5·R10 관측 못 함 — [확인 기록](../../android/device-checklist.md#확인-기록-2026-10-2930-지금-코드-커밋-전). 2026-11-04 `42b1dcc`(120초·10초)로 1~3·8·9·12·R9 범위와 정지 뒤 앱 재실행을 관측했고, 14번은 창 1개·인증 후 추가 창 없음만 관측상 통과입니다 — [2026-11-04 확인 기록](../../android/device-checklist.md#확인-기록-2026-11-04-42b1dcc)
- WebView 셸(#25): debug는 개발 서버를 열고 신뢰 origin 밖 탐색을 막습니다. release는 웹 주소 미설정으로 아무것도 열지 않습니다. 다른 origin으로 가는 메인 프레임 POST의 최초 요청에 빈 응답을 돌려줍니다(실제 전송 차단은 미관측, [범위와 한계](../../android/webview-shell.md#외부-요청-차단-범위와-한계)). Android 단위 테스트는 #22 포함 전체 106건입니다. 실기기(2026-11-06, 임시 fixture 화면): W1·W2·W5(debug) 관측상 통과(사용자 관측), W6 부분 관측(관측 구간에서 수신기에 외부 POST 기록 없음, 앱 안내 문구 미확인) — [확인 기록](../../android/webview-shell.md#확인-기록-2026-11-06-fixture)
- 연결 단계: #88에서 Android 내부 WebView 브리지 v1과 기기 인증 상태 연결을 로컬 구현했습니다. release 시작 주소와 신뢰 origin은 `https://beonepay.site` 하나이며, HTTP API·실제 프런트엔드 연결과 실기기·설치 확인은 아직 없습니다.

## 아직 안 되는 것

- 실기기: #22의 `정리 중` 경로(R5·R10)·앱 취소 뒤 최종 결과·창이 사라진 정지·최종 결과가 아직 오지 않은 정지 상태의 재실행 복구, 7·16·17번. #25의 W3·W3a·W3b·W4·W5(release), W6의 안내 문구 확인과 콘솔 절차, 실제 프런트 화면 기준 확인. 다음 시험 절차는 [#22 확인 목록](../../android/device-checklist.md#다음-시험-준비-설치본-확인과-남은-항목-2026-10-31-미실행)과 [`docs/android/webview-shell.md`](../../android/webview-shell.md)에 있습니다.
- WebView로 실제 React 화면과 서버를 통합 확인(지금 React 화면은 제목 하나뿐), #88 로컬 브리지의 실제 프런트엔드·실기기 검증
- `가상 주문서 결제` 버튼과 모의 승인 흐름

## 현재 작업과 PR

- [#22](https://github.com/beone-capston/beone-mvp/issues/22) 기기 인증과 60초 활성 — [PR #24](https://github.com/beone-capston/beone-mvp/pull/24)로 병합. 미해결 스레드 2건(복구 조건, 세션·콜백 귀속 한계)은 브리지 연결 전 다시 검토할 조건입니다. 남은 실기기 항목·정책 결정 대기 ([단계 기록](22-auth-session.md))
- [#25](https://github.com/beone-capston/beone-mvp/issues/25) WebView 셸 기초 — [PR #26](https://github.com/beone-capston/beone-mvp/pull/26)으로 `develop`에 squash 병합(`5a0065f6f7f86f655cb417ccca3db07817737e09`). 작업 브랜치와 워크트리는 보존 중입니다. 실기기는 W1·W2·W5(debug) 관측상 통과, W6 부분 관측이며 나머지 항목은 미실행입니다 — [확인 기록](../../android/webview-shell.md#확인-기록-2026-11-06-fixture) ([단계 기록](25-webview-shell.md))
- [#86](https://github.com/beone-capston/beone-mvp/issues/86) 브리지 v1 계약 확정(문서) — 2026-11-16 사용자 결정 반영, [PR #87](https://github.com/beone-capston/beone-mvp/pull/87)로 `develop`에 병합(`73d7f045b5166594d7851944353e5ecde0eab37e`). 공개 계약 v1은 문서로 확정됐고, 구현 상태는 아래 #88을 따릅니다 ([단계 기록](86-bridge-contract-v1.md))
- [#88](https://github.com/beone-capston/beone-mvp/issues/88) 브리지 v1 로컬 구현 — [PR #89](https://github.com/beone-capston/beone-mvp/pull/89)에서 최신 `develop` 충돌 해결 중. release 시작 주소 `https://beonepay.site/`, 신뢰 origin 1개에만 `window.BeoneBridge` 노출, 기기 인증 상태와 연결(대기 시간 120초·10초는 근거 없는 잠정값). 로컬 단위 테스트 166건만 실행했고 실기기·설치·실제 프런트엔드 통합은 미확인입니다 ([단계 기록](88-android-bridge-local.md))
- [#27](https://github.com/yim0327/beone-mvp/issues/27) 브리지 계약 초안(후보) — [PR #72](https://github.com/beone-capston/beone-mvp/pull/72)로 병합(PR #28을 대체). 당시 공개 계약은 후보였습니다. 현재 공개 계약 v1은 #86에, Android 로컬 구현 상태는 #88에 기록합니다

## 사용자 확인 사항

1. **release 구현과 정확한 origin** — 2026-11-16 사용자 결정으로 release 시작 주소 `https://beonepay.site/`와 신뢰 origin `https://beonepay.site`를 정했습니다(#86, 문서 확정). #88은 이를 Android 로컬 구현에 반영했지만 release 실기기·실제 서버 화면 통합은 확인하지 않았습니다. 앱 에셋(`WebViewAssetLoader`) 권장안은 과거 이력입니다.
2. **브리지 계약** — D-21 v1 계약은 #86에서 문서로 확정됐고, #88에서 Android 로컬 구현으로 연결했습니다. 프런트엔드와의 실제 화면·서버 통합, 실기기 검증, `WEB_MESSAGE_LISTENER` 미지원 WebView의 안내·차단 정책은 별도입니다.
3. **#22 운영 값** — 응답 대기 시간, 정지 뒤 복구 방식, 화면이 보일 때만 인증을 요청하는 조건(RESUMED).
4. **실기기 시험 승인** — #22 16·17번(기기 보안 설정 변경), #25 W3 계열 실행 방식·release 시험용 서명(W4·W5(release))·실제 프런트 화면 기준 확인·W6 앱 안내 문구 확인과 콘솔 절차, 추가 관측을 위한 기기 설치·조작. 완료 범위: #25 W1·W2·W5(debug)는 2026-11-06 임시 fixture에서 사용자 직접 관측 기준 통과(W2는 `webview-shell.md`에 적힌 기준대로 회전 전후 주소·웹 기록 유지로 판정, JS 상태는 기록하지 않음), W6는 부분 관측(지정 관측 구간에서 수신기에 B 기록 없음, 앱 안내 문구 미확인, 콘솔 절차 미실행) — [확인 기록](../../android/webview-shell.md#확인-기록-2026-11-06-fixture).
5. **리뷰** — #25는 `21652b2`에 CodeRabbit 리뷰 1건이 있고 지적 4건에 대응했습니다. 보완 커밋 리뷰는 아직 없습니다. 자동 리뷰는 사람 승인이 아닙니다.
6. **실제 프런트 인계** — 실제 화면 응답의 CSP와 리디렉션 조건 ([인계 항목](../../android/webview-shell.md#외부-요청-차단-범위와-한계)).

## 다음 작업

- 기기 승인 뒤: #88 release 로딩·브리지 로컬 구현의 실기기 확인, 실제 프런트엔드·서버 화면 통합 확인, 계측 테스트
- README·architecture 충돌은 #25의 `develop` 통합 커밋 `5f7a46b`에서 해결했습니다.
