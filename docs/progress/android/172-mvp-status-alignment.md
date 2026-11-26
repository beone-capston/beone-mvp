# #172 MVP 실행 안내·개발 현황 문서 정합 (D1~D5)

브랜치 `docs/172-mvp-status-alignment`, 시작 기준 `origin/develop` `d3ab9f5`. 결정 36의 Android 갭 조사에서 나온 문서 불일치 D1~D5를 고칩니다. 결정·정책 내용은 바꾸지 않았습니다.

## 2026-11-24 · D1~D5 문장 정합

- 배경: #88(release 시작 주소), #163·#167(프런트 주문·모의 결제 흐름), 결정 37 O2-L(member 경로 Vite 프록시) 이후에도 일부 안내가 당시 상태를 현재처럼 적고 있었습니다.
- 한 일
  - D1 [`docs/decisions.md`](../../decisions.md) D-25 2026-11-16 보충: 'release `WEB_START_URL`은 빈 문자열' 문장은 당시 기록이라 그대로 두고, 그 아래에 2026-11-24 확인 보충 한 줄을 더했습니다. 현재 빌드 값은 `https://beonepay.site/`(`android/app/build.gradle.kts:31`)이며, 확정 주소(D-25 결정)와 현재 빌드 사실을 나눠 적었습니다.
  - D2 [`README.md`](../../../README.md) 프런트엔드 메인화면 절: '실제 API 연결, 혜택 계산, Android 인증은 아직 연결하지 않았다'를 현재 사실로 바꿨습니다. 근거 소스는 `frontend/src/orders/orderApi.ts`·`frontend/src/payment/paymentApi.ts`(`X-BeONE-Demo-User`, `credentials: 'omit'`, `/api/v1/me/...`), `frontend/src/hooks/useNativeAuth.ts`(`window.BeoneBridge`), `frontend/src/App.tsx`(카드 추가·마이페이지·혜택 메뉴의 준비 중 안내)입니다. member 대응은 N-FE-COORD·N-FE-PROXY 대기, 실기기 통합 확인은 미실행으로 적었습니다.
  - D3 [`docs/progress/android/README.md`](README.md) '아직 안 되는 것': 'React 화면은 제목 하나뿐'과 '`가상 주문서 결제` 버튼과 모의 승인 흐름' 항목을 고쳤습니다. 프런트 흐름은 있고(#163·#167) 남은 것은 실기기 통합 확인(BR1~BR16)입니다. 이 파일의 다른 항목은 건드리지 않았습니다.
  - D4 [`docs/android/webview-shell.md`](../../android/webview-shell.md) W4: 기대 결과 칸에 '#88 이전 기준, 현재는 BR22가 대체'를 붙였습니다. 기존 행과 상태 칸은 그대로입니다.
  - D5 `android/app/build.gradle.kts` debug 주석과 `README.md` Android 절: `adb reverse`를 5173·8080 둘 다 연결하도록 고쳤습니다. local·demo는 두 포트를 쓰고(D-23 11항), member의 같은 origin 프록시는 확정(결정 37 O2-L)이지만 프런트 설정 전(N-FE-PROXY)이라 지금도 두 포트가 필요하다고 적었습니다. 주석 한 줄만 바꿔 줄 수가 같고, `WEB_START_URL` release 값은 31행에 그대로 있습니다.
- 검증: 바꾼 문서의 상대 링크·앵커가 실제 경로인지 확인했습니다. `build.gradle.kts`는 diff로 주석 한 줄만 바뀌었음을 확인하고 `cd android && ./gradlew help -q`로 설정 평가를 확인했습니다. 문서·주석만 바뀌어 테스트는 실행하지 않았습니다.
- 하지 않은 것: 결정 본문 덮어쓰기, D8 표기·`mock-payments.md:134` 등 미결(N-G3-2·D8) 문구 수정, frontend·Android 제품 코드 수정.
- 남은 것: 실기기 통합 확인(BR1~BR24, 사용자 승인), N-FE-PROXY·N-FE-COORD 팀원 조율, 미결 답 이후 관련 문구 정리.
