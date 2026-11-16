# 브리지 v1 시험 페이지(T)

> **시험 전용·제거 가능.** 이 폴더는 [브리지 v1 실기기 확인 순서표](../../docs/android/device-checklist.md#브리지-v1-실기기-확인-순서표)(BR1~BR24)의 "준비"가 요구하는 시험 페이지(T)입니다. 제품 화면(`frontend/`)이 아니고 앱·서버 코드가 이 폴더를 참조하지 않으므로, 시험이 끝나면 폴더째 지워도 됩니다. 페이지 동작은 코드 리뷰로만 확인했고 브라우저·기기에서 실행하지 않았습니다.

근거: 2026-11-17 사용자 결정(시험 페이지를 frontend 밖 별도 정적 페이지로 둠), [브리지 v1 계약](../../docs/android/bridge-contract-candidate.md).

## 파일

| 파일 | 역할 |
| --- | --- |
| [`index.html`](index.html) | 최상위 시험 화면. BR1~BR18·BR21·BR24용 |
| [`frame.html`](frame.html) | iframe 안에 들어가는 작은 시험 화면. BR19(같은 origin)·BR20(다른 origin)용 |
| [`test.js`](test.js) | 두 화면이 함께 쓰는 스크립트 |
| [`style.css`](style.css) | 화면 스타일 |

빌드 도구·의존성이 없는 순수 HTML·JS입니다. 외부 스크립트·CDN·추적·분석을 쓰지 않고, 네트워크로 아무것도 보내지 않습니다. 두 HTML에는 같은 origin의 파일만 읽도록 CSP(Content-Security-Policy, 페이지가 읽어도 되는 출처를 브라우저에 알려 주는 규칙)를 넣었습니다. `index.html`만 debug 다른 origin iframe용으로 `http://127.0.0.1:*`의 iframe을 더 허용합니다. 외부로 가는 주소는 BR24용 링크 2개(`https://www.beonepay.site/`, `http://beonepay.site/`)뿐이며, 누를 때만 이동을 시도합니다.

## 할 수 있는 일

| 순서표 "준비"의 요구 | 위치 |
| --- | --- |
| `typeof window.BeoneBridge` 표시 | 두 화면의 "현재 프레임"(로드 때 표시, [다시 확인하고 기록]) |
| 입력 문자열을 그대로 `postMessage`로 보내고 `length` 표시 | "보내기". 입력란 값을 가공하지 않고 보내며, JavaScript `length`(UTF-16 코드 단위, Kotlin `String.length`와 같은 단위)를 입력 중에도 표시하고 기록에 함께 남김 |
| 4096/4097 경계 문자열 | "BR17 크기 경계" ①~⑤. `{"v":1,"type":"state.get","pad":"…"}`의 `pad`를 ASCII·한글(`가`)·이모지(`😀`, 길이 2) 하나+ASCII로 채워 순서표 그대로 4096·4097을 만듦 |
| 받은 메시지를 받은 시각과 함께 기록 | "기록". 받은 메시지·보낸 메시지·메모를 시각(UTC, ISO 8601)과 함께 차례로 표시 |
| 같은 origin iframe과 다른 origin iframe | "BR19"(`frame.html`), "BR20"(`http://127.0.0.1:5174/frame.html`, 아래 두 번째 포트) |
| 새로고침에도 남는 기록과 지우기 | 기록을 그 origin의 `localStorage`에 프레임 종류별로 보관(최근 500건). [기록 지우기]. 저장소를 쓸 수 없으면 화면에 그렇게 표시 |
| 계약 메시지 예시 | "계약 메시지 6종". 웹 → 네이티브 3종과, 네이티브 → 웹 `type` 3종을 웹이 보내는 예시(기대값 `UNKNOWN_TYPE`, BR18⑨와 같은 성격). 순서표 BR18 ①~⑩도 버튼으로 둠 |
| BR21 문자열이 아닌 메시지 | [ArrayBuffer 보내기]. WebView가 지원하지 않아 예외가 나면 기록에 남김 |
| BR13 페이지 이동·재생성 구분 | 주소와 `history.length` 표시, 같은 페이지로 이동하는 링크, 페이지 로드마다 "페이지 로드" 메모 |

예시 버튼은 입력란만 채우고, 실제 전송은 [보내기]로 합니다. 계약에 없는 메시지 종류나 값은 만들지 않았습니다(BR17의 `pad`와 BR18의 오류 문자열은 순서표에 적힌 그대로입니다).

## debug에서 쓰기 (PC + 실기기)

명령은 모두 사용자가 직접 실행합니다. 프런트엔드 개발 서버(`npm run dev`)도 5173을 쓰므로 먼저 꺼 둡니다.

### 1. PC에서 정적 서빙

저장소 루트에서 실행합니다. 첫 번째는 T, 두 번째는 BR20의 다른 origin iframe용입니다. 각각 다른 터미널에서 켜 둡니다.

```bash
python3 -m http.server 5173 --bind 127.0.0.1 --directory tools/bridge-test
python3 -m http.server 5174 --bind 127.0.0.1 --directory tools/bridge-test
```

`python3`가 없으면(macOS에서는 Xcode Command Line Tools를 설치하지 않은 경우) 새로 설치하지 말고, 프런트엔드 의존성(`cd frontend && npm ci`)으로 이미 들어 있는 Vite를 씁니다. `--no-install`이라 아무것도 내려받지 않습니다.

```bash
cd frontend && npx --no-install vite ../tools/bridge-test --host 127.0.0.1 --port 5173 --strictPort
cd frontend && npx --no-install vite ../tools/bridge-test --host 127.0.0.1 --port 5174 --strictPort
```

Vite는 개발용 스크립트(`/@vite/client`)를 같은 origin에서 끼워 넣습니다. T의 동작에는 영향이 없지만, 기록에 없는 연결이 개발자 도구에 보일 수 있습니다.

PC 브라우저에서 `http://localhost:5173/`을 열면 화면 모양만 미리 볼 수 있습니다(브리지가 없으므로 `typeof`는 `undefined`).

### 2. 기기 연결

```bash
adb reverse tcp:5173 tcp:5173
adb reverse tcp:5174 tcp:5174
```

### 3. debug 앱이 T를 열게 하기

debug 앱의 기본 시작 주소가 `http://localhost:5173/`이므로, 위 서빙을 켠 상태라면 따로 바꿀 것이 없습니다. 다른 주소를 쓰려면 루트 [`README.md`](../../README.md)의 Android 절에 있는 방법 그대로 `cd android && ./gradlew installDebug -Pbeone.devServerUrl=<주소>`로 다시 설치합니다. 신뢰 origin은 그 주소의 origin이 됩니다.

- 다른 origin iframe은 `http://127.0.0.1:5174/frame.html`입니다. 호스트(`127.0.0.1`)가 신뢰 origin(`http://localhost:5173`)과 다르므로 다른 origin입니다. debug의 평문 HTTP 예외가 `localhost`·`127.0.0.1`·`10.0.2.2`뿐이라 이 조합을 씁니다.
- 두 번째 포트를 바꾸려면 T 주소에 `?xport=<포트>`를 붙입니다(1024~65535). 다른 origin iframe은 T의 호스트가 `localhost`일 때만 둡니다.
- WebView가 다른 origin iframe의 이동을 막으면 iframe이 비어 있을 수 있습니다. 순서표 BR20은 이 경우를 그대로 기록하게 되어 있습니다.

## release에서 쓰기

release 앱은 `https://beonepay.site/`만 열고 주소창이 없습니다. T를 같은 origin의 `/bridge-test/`에 올리고 기존 정적 확인 화면에 임시 링크를 다는 절차는 [브리지 시험 페이지 서버 적용 절차](../../docs/deployment/bridge-test-page.md)에 있습니다(사용자 실행, 미검증). release에서는 다른 origin iframe을 두지 않습니다.

## 기록할 때

- 순서표 "기록" 규칙을 따릅니다. 화면 기록을 옮길 때 기기 일련번호 같은 식별 정보, 생체·PIN 값, 개인 정보가 보이는 화면은 적지 않습니다.
- T의 기록은 그 기기의 WebView 저장소에만 있습니다. 앱 데이터를 지우거나 앱을 지우면 함께 사라집니다.

## 제거

이 폴더(`tools/bridge-test/`)를 지우고, 이 폴더를 가리키는 문서 링크([순서표 "준비"](../../docs/android/device-checklist.md#준비), [서버 적용 절차](../../docs/deployment/bridge-test-page.md), Android 진행 기록)를 함께 정리합니다. 앱·프런트엔드·백엔드 코드에는 바꿀 것이 없습니다. 서버에 올렸다면 서버 절차의 제거 단계를 따릅니다.
