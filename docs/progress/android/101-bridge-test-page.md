# #101 브리지 실기기 시험 페이지(T)와 서버 적용 절차

- 이슈: [#101](https://github.com/beone-capston/beone-mvp/issues/101)
- 브랜치: `feat/101-bridge-test-page` (기준 `origin/develop` `a19dc25ad481a8be8308cc111e9ac579877de72d`)
- 근거 결정: 2026-11-17 사용자 결정(시험 페이지는 frontend 밖 별도 정적 페이지, release 확인용 서버 절차를 준비하고 실제 적용은 사용자가 직접 함), [브리지 v1 계약](../../android/bridge-contract-candidate.md), [브리지 v1 실기기 확인 순서표](../../android/device-checklist.md#브리지-v1-실기기-확인-순서표)
- 성격: 시험 전용 정적 페이지와 사용자 실행 절차 문서입니다. 제품 기능·계약 변경이 아니고, `frontend/`·`android/`·`backend/` 코드는 바꾸지 않았습니다.

## 2026-11-17 · 시험 페이지·사용 방법·서버 절차 · PR 대기

### 한 일

- [`tools/bridge-test/`](../../../tools/bridge-test/README.md) 신규: `index.html`(최상위 T), `frame.html`(BR19·BR20 iframe), `test.js`(공용 스크립트), `style.css`, `README.md`(사용 방법, 시험 전용·제거 가능)
  - 순서표 "준비"의 요구: `typeof window.BeoneBridge` 표시, 입력 문자열을 그대로 `postMessage`로 보내고 JavaScript `length` 표시, 받은 메시지를 시각과 함께 기록, 같은 origin iframe(`frame.html`)과 다른 origin iframe(debug `http://127.0.0.1:5174/frame.html`)
  - BR13용: 기록을 같은 origin `localStorage`에 보관(새로고침에도 남음, 최근 500건)과 [기록 지우기], 주소·`history.length` 표시, 페이지 로드 메모
  - BR17 ①~⑤ 경계 문자열 생성(ASCII·한글·이모지, 4096/4097), BR18 ①~⑩ 문자열, BR21 ArrayBuffer 전송, BR24 링크 2개
  - 계약 메시지 6종 예시: 웹 → 네이티브 3종과, 네이티브 → 웹 `type` 3종을 웹이 보내는 예시(기대 `UNKNOWN_TYPE`, #88 구현 결정과 BR18⑨). 계약에 없는 메시지 종류·값은 만들지 않았습니다.
  - 외부 스크립트·CDN·추적·네트워크 전송 없음. 두 HTML에 같은 origin만 허용하는 CSP를 넣었습니다(`index.html`은 debug 다른 origin iframe용 `http://127.0.0.1:*` iframe만 추가 허용). 화면 출력은 `textContent`로만 합니다.
- [`docs/deployment/bridge-test-page.md`](../../deployment/bridge-test-page.md) 신규: 사전 대조(읽기만) → 백업(e) → `/bridge-test/` 배치(a) → 정적 확인 화면 임시 링크(b) → BR24 링크 확인(c) → 시험 → 제거·원상 복구 확인(d) → 되돌리기(e). 단계마다 기대 결과·멈춤 조건. 서버의 정적 루트와 nginx 구성은 지어내지 않고 사전 대조로 확인하게 했습니다. nginx 설정 변경·재시작, 비용·보안그룹·DNS 변경은 넣지 않았습니다.
- 연결: [순서표 "준비"](../../android/device-checklist.md#준비)에 T 위치와 서버 절차 링크 한 줄(기존 항목·번호는 그대로), [Android 진행 상황](README.md)의 현재 작업 줄

### 검증

- `git diff --check`: 출력 없음
- 변경 파일이 담당 경로(`tools/bridge-test/`, `docs/deployment/bridge-test-page.md`, `docs/android/device-checklist.md`, `docs/progress/android/`)만인지 `git diff --name-only`로 확인
- 외부 요청: `tools/bridge-test/`의 `http(s)://`로 시작하는 주소를 grep으로 확인. 외부 주소는 BR24 링크 2개(`href`)와 debug 다른 origin iframe(`127.0.0.1`, CSP·스크립트 안)뿐이고, 외부 `src`는 없습니다.
- `node --check tools/bridge-test/test.js`: 문법 오류 없음. 경계 문자열 생성 함수만 Node로 따로 계산해 ①~⑤의 길이(4096·4097·4096·4097·4097)와 JSON 해석 결과(`v`, `type`)를 확인했습니다.
- 상대 링크·앵커: 새로 넣은 링크의 대상 파일과 제목 앵커(`#준비`, `#브리지-v1-실기기-확인-순서표`, `#구성`, `#범위와-현재-상태`, `#d-25-확정-정책`)가 있는지 확인
- **페이지 동작은 코드 리뷰로만 확인했습니다.** 브라우저·WebView·기기에서 실행하지 않았습니다. Gradle·adb·설치·서버 적용은 실행하지 않았습니다.

### 범위 밖으로 남긴 것

- 실제 순서표 실행(BR1~BR24)과 결과 기록, 서버 적용: 사용자가 직접 합니다.
- 서버로 파일을 옮기는 경로: 정하지 않았습니다(절차 0절).
- 문서 갱신이 필요 없는 곳: `docs/architecture.md`·`docs/domains/**`는 도메인·패키지·앱 코드 변경이 없어 고치지 않았습니다. 루트 `README.md`는 담당 경로가 아니어서 고치지 않았습니다.

## 2026-11-17 · CodeRabbit 리뷰 반영 (head `2d91ccf`)

CodeRabbit 제출 리뷰 1건(지적 4건, 사람 승인 아님)을 코드·문서로 확인해 4건 모두 반영했습니다.

- 서버 절차 3절: 업로드 폴더 변수 `U`를 정의·확인하는 단계를 추가하고, SHA-256 대조(3-1)와 배치(3-2)를 나눠 네 값이 모두 같을 때만 배치하게 했습니다.
- 서버 절차 4절: 수정본 생성·검토(4-1, 생성 성공·링크 1건·`diff`)와 덮어쓰기(4-2)를 나눠, 확인이 끝난 뒤에만 정적 확인 화면을 덮어쓰게 했습니다.
- `test.js`: `localStorage` 쓰기가 실패한 뒤에는 저장소의 이전 값 대신 메모리 기록을 기준으로 표시·추가합니다(읽기는 되고 쓰기만 실패하는 경우 포함).
- 재검증: `git diff --check`, `node --check`. 페이지 동작은 여전히 코드 리뷰로만 확인했습니다.
