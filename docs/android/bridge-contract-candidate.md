# Android WebView 브리지 계약

> **상태: 확정(v1, 2026-11-16 사용자 결정), 구현 전.** 2026-11-04 사용자의 설계 방향 결정([D-21](../decisions.md#d-21-확정-정책))과 2026-11-16 사용자 결정(D-21·[D-25](../decisions.md#d-25-확정-정책)의 2026-11-16 보충)으로 이 문서의 공개 브리지 계약을 `v=1`로 확정했습니다. 다른 문서의 링크가 깨지지 않도록 파일 이름(`bridge-contract-candidate.md`)은 그대로 둡니다. 이름에 `candidate`가 남아 있어도 이 문서의 계약은 후보가 아니라 확정입니다. 확정한 것은 문서상의 계약입니다. 브리지 코드와 release 설정 반영은 별도 이슈이며 아직 하지 않았고, 실기기 확인은 별도 승인을 받아야 합니다. 이 계약을 바꾸려면 사용자 결정이 필요합니다([운영 규칙](../development/autonomous-workflow.md#스스로-정할-것과-사용자에게-물을-것)). 인증 대기 120초·정리 대기 10초는 이 계약의 확정값이 아닙니다. `auth.request` 연결에 기존 `AuthRequestGate` 값을 잠정 사용하는 근거 없는 제안값이며, 실기기 관측 전 미확정입니다(7절).

근거: [PRD §4.3·FR-06](../PRD.md), [D-08](../decisions.md#d-08-확정-정책), [실기기 PoC](../d08-biometrics-poc.md)(PoC에서 `addWebMessageListener`로 성공·취소·불일치·잠금 상태 전달과 복귀 시 동기화를 확인).

## 1. 지키려는 원칙

- **네이티브가 기준입니다.** 60초 활성 여부와 남은 시간은 Android가 단조 시계로 판단합니다. 웹의 카운트다운은 표시용입니다. 웹이 "활성"이라고 주장해도 네이티브 상태를 바꾸지 않습니다.
- **필요한 값만 보냅니다.** 생체 정보, PIN, 시스템 오류 메시지 원문, 기기 식별자는 보내지 않습니다. 인증 수단은 `BIOMETRIC`·`DEVICE_CREDENTIAL` 구분까지만 보냅니다.
- **트리거일 뿐입니다.** 인증 결과는 앱 안의 가상 결제 화면을 여는 데만 씁니다. 웹이 이 값을 서버에 결제 승인 근거로 보내지 않고, 서버도 신뢰하지 않습니다(PRD FR-06).
- **신뢰 origin에만 노출합니다.** origin은 스킴·호스트·포트의 묶음입니다. `https://beonepay.site`, `https://www.beonepay.site`, `http://beonepay.site`는 서로 다른 origin입니다. 허용 origin 목록은 빌드 변형별로 따로 둡니다. debug 목록은 개발 서버 `http://localhost:5173`이고, 테스트 origin은 계측 테스트 코드가 주입하며 debug 목록에 넣지 않습니다. release는 시작 주소 `https://beonepay.site/`를 열고, 탐색과 브리지의 신뢰 origin은 정확히 `https://beonepay.site` 하나입니다. release 목록에는 `www` 같은 다른 호스트, debug·테스트 origin, 평문 HTTP(`http://`) origin을 넣지 않습니다. 와일드카드를 쓰지 않습니다. 모든 변형에 공통으로 쓰는 전역 목록은 두지 않습니다.
- **최상위 프레임만 처리합니다.** 브리지 구현은 허용 origin이라도 iframe 등 하위 프레임에서 온 메시지(`WebMessageListener`의 `isMainFrame`이 `false`)를 처리하지 않아야 합니다. 인증 창을 띄우거나 상태를 바꾸지 않고, 그 프레임에 `state`·`error`를 포함한 어떤 응답도 보내지 않습니다(무시). 브리지가 아직 없으므로 현재 동작이 아니라 구현이 지켜야 할 계약과 시험 요구입니다.

## 2. 전달 방식

| 방식 | 내용 | 평가 |
| --- | --- | --- |
| A (확정 v1, D-21) | AndroidX WebKit `WebViewCompat.addWebMessageListener`에 허용 origin 규칙을 지정 | origin을 플랫폼이 검사합니다. PoC에서 동작을 확인했습니다. `androidx.webkit` 의존성과 WebView 기능 지원 확인이 필요합니다 |
| B (쓰지 않음) | `addJavascriptInterface` | 모든 프레임·origin에 노출되기 쉬워 쓰지 않습니다 |

### 브리지 전역 객체 이름

- 이름은 **`BeoneBridge`**입니다(2026-11-16 사용자 결정). `addWebMessageListener`의 `jsObjectName`으로 지정하며, 계약 문서·Android 구현·웹 코드에서 대소문자까지 같게 씁니다.
- 웹은 메시지를 JSON 문자열로 만들어 이 객체의 `postMessage`로 보냅니다.

```js
window.BeoneBridge.postMessage(JSON.stringify({ v: 1, type: "state.get" }));
```

- 플랫폼은 허용 origin 규칙에 맞는 프레임에만 이 객체를 넣습니다. origin·최상위 프레임 확인과 메시지 검증 규칙(3절)은 그대로이며, 객체 이름이 이 규칙을 대신하지 않습니다.

## 3. 메시지

모든 메시지는 JSON 문자열이고 `v`(계약 버전)와 `type`을 가집니다. 최상위 프레임의 신뢰 origin에서 온 메시지가 크기 상한을 넘거나, 형식이 잘못됐거나, `v`를 지원하지 않거나, `type`을 모르면 **처리를 거부**합니다. 처리 거부는 메시지를 처리하지 않고, 인증 상태를 바꾸지 않으며, `error`를 보내는 것입니다. 신뢰하지 않는 origin이나 하위 프레임 메시지의 **무시**(어떤 응답도 보내지 않음)와 다르고, 형식이 맞는 `auth.request`를 지금 받을 수 없을 때 `state`를 다시 보내는 경우와도 다릅니다.

### 웹 → 네이티브

| type | 필드 | 의미 |
| --- | --- | --- |
| `auth.request` | 없음 | 기기 인증 창을 띄워 달라는 요청. `state.get` 응답을 기다리지 않고 보낼 수 있습니다. 네이티브가 새 요청을 받을 수 없는 단계면 인증 창을 다시 띄우지 않고 현재 상태를 다시 보냅니다. 거부되거나 라이브러리에 전달되지 않은 경우에도 별도 메시지 없이 현재 상태를 다시 보냅니다. 활성 중 요청은 재인증으로 처리해 성공 시 60초를 새로 시작합니다(#22 구현) |
| `state.get` | 없음 | 현재 상태를 다시 보내 달라는 요청(화면 진입·복귀 시) |
| `payment.check` | 없음 | `가상 주문서 결제` 버튼을 누르기 직전, 지금 활성인지 네이티브에 다시 묻는 요청 |

### 네이티브 → 웹

| type | 필드 | 의미 |
| --- | --- | --- |
| `state` | `active: boolean`, `remainingMs: number`, `reason: string`, `method?: string`, `authenticating: boolean` | 현재 상태. 상태가 바뀔 때마다, 그리고 `state.get` 응답으로 보냅니다. 단, 60초 만료는 밀어 보내지 않습니다. 웹이 `remainingMs`가 끝나면 `state.get`을 보내 만료 상태를 받습니다(B5). `authenticating`: 네이티브가 새 인증 요청을 받을 수 없는 동안 `true`(인증 중·정리 중·정지 포함). `method`: 인증 수단을 알 수 없으면(`UNKNOWN`) 보내지 않습니다 |
| `payment.check.result` | `allowed: boolean`, `reason: string` | `payment.check`의 응답. `reason`의 값 집합은 `state.reason`과 같습니다(아래 목록). `allowed=false`면 웹은 결제 요청을 보내지 않고 재인증을 안내합니다 |
| `error` | `code: string` | 처리를 거부한 사유. `UNKNOWN_TYPE`: 모르는 `type`. `UNSUPPORTED_VERSION`: 지원하지 않는 `v`. `INVALID_MESSAGE`: 크기 상한 초과, JSON이 아니거나 필수 필드가 없는 등 앞의 둘로 나타낼 수 없는 형식 오류. 응답의 `v`는 아래 버전 오류 규칙을 따릅니다 |

`reason` 값(`state.reason`과 `payment.check.result.reason` 공통): `NOT_AUTHENTICATED`, `EXPIRED`, `AUTH_FAILED`, `AUTH_CANCELED`, `LEFT_APP`, `SETUP_REQUIRED`, `UNAVAILABLE`, `NO_RESPONSE`. 활성 상태면 `ACTIVE`. #22 구현의 `InactiveReason`과 같습니다. `NO_RESPONSE`는 인증 응답 대기 시간 초과(정지)이며, 웹은 "앱을 완전히 종료한 뒤 다시 열어 주세요"를 안내합니다. 네이티브가 자동으로 다시 인증하지 않습니다.

### 검사 순서

네이티브는 웹에서 받은 메시지를 아래 순서로 검사하고, 먼저 걸린 단계의 처리만 합니다.

1. **신뢰 origin·최상위 프레임 확인.** 신뢰 origin이 아니거나 하위 프레임(`isMainFrame=false`)이면 무시합니다. 처리하지 않고 `error`를 포함한 어떤 응답도 보내지 않습니다.
2. **크기.** 상한을 넘으면 JSON을 해석하지 않고 `INVALID_MESSAGE`를 보냅니다(아래 크기 상한).
3. **JSON 형식.** JSON이 아니거나 형식이 잘못됐으면 `INVALID_MESSAGE`를 보냅니다.
4. **`v`.** `v`가 없으면 `INVALID_MESSAGE`, 숫자 `1`이 아니면 `UNSUPPORTED_VERSION`을 보냅니다.
5. **`type`.** 모르는 `type`이면 `UNKNOWN_TYPE`을 보냅니다.

2~5단계의 오류는 인증 상태를 바꾸지 않습니다.

### 크기 상한

- 대상은 웹 → 네이티브 메시지입니다.
- 상한은 **4096 UTF-16 코드 단위**입니다. 네이티브가 받은 문자열의 Kotlin `String.length` 값으로 잽니다. 길이 4096은 허용하고, 4097부터 거부합니다.
- UTF-16 코드 단위는 UTF-8 바이트 수나 사람이 보는 글자 수와 다릅니다.
  - 영문·숫자·기호(ASCII) 1자: UTF-16 1, UTF-8 1
  - 한글 1자(예: `가`): UTF-16 1, UTF-8 3
  - 보충 문자 1자(예: 이모지 `😀`, U+1F600): UTF-16 2(서로게이트 쌍), UTF-8 4
  - 따라서 한글 4096자는 허용되지만 UTF-8로는 12288바이트이고, 보충 문자는 2048자만 넣어도 4096에 이릅니다.
- 상한을 넘으면 JSON을 해석하지 않고, 인증 상태를 바꾸지 않으며, 기존 코드 `INVALID_MESSAGE`를 보냅니다. 크기 전용 오류 코드는 없습니다.
- 참고로 현재 정상 메시지는 모두 ASCII이고 가장 긴 `{"v":1,"type":"payment.check"}`가 30입니다.

### 버전 오류 규칙(v1)

- 이 계약이 지원하는 버전은 숫자 `v=1` 하나입니다.
- 모든 `error` 응답의 `v`는 항상 숫자 `1`입니다. 입력의 `v`를 응답에 그대로 복사하지 않습니다.
- `v`가 없으면 `INVALID_MESSAGE`, `v`가 있지만 숫자 `1`이 아니면 `UNSUPPORTED_VERSION`입니다.
- `UNKNOWN_TYPE`·`INVALID_MESSAGE`에도 같은 응답 `v`를 씁니다.
- 오류가 겹치면 위 검사 순서(크기, JSON 형식, `v`, `type`)에서 먼저 걸린 코드를 보냅니다. `UNKNOWN_TYPE`은 `v`가 숫자 `1`일 때만 나옵니다. 예: `{"type":"unknown"}`은 `INVALID_MESSAGE`, `{"v":2,"type":"unknown"}`은 `UNSUPPORTED_VERSION`입니다.
- 신뢰하지 않는 origin과 하위 프레임 메시지는 이 규칙과 무관하게 무시하며 `error`도 보내지 않습니다(검사 순서 1단계).

버전 협상 기능은 두지 않습니다. 이 규칙은 확정된 v1 계약이며, 구현은 아직 없습니다.

예시:

```json
{"v":1,"type":"state","active":true,"remainingMs":60000,"reason":"ACTIVE","method":"BIOMETRIC","authenticating":false}
```

```json
{"v":1,"type":"state","active":false,"remainingMs":0,"reason":"LEFT_APP","authenticating":false}
```

```json
{"v":1,"type":"payment.check.result","allowed":false,"reason":"EXPIRED"}
```

```json
{"v":1,"type":"error","code":"UNSUPPORTED_VERSION"}
```

## 4. 흐름

```text
가상카드 화면 진입 → 인증 시작 (B3: 웹이 요청, 창은 네이티브)
  웹: auth.request (state.get 응답을 기다리지 않음)
→ 네이티브: 인증 창 (새 요청을 받을 수 없으면 생략하고 state만 전송) → 성공 → state(active, 60000)
→ 웹: 버튼 활성 + 표시용 카운트다운
→ 버튼 누름 → 웹: payment.check → 네이티브: payment.check.result(allowed)
→ 웹 → 서버: 모의 승인 요청 (서버는 인증 결과를 신뢰 근거로 쓰지 않음)

실패·취소·앱 이탈 → 네이티브: state(비활성, reason) → 웹: 버튼 비활성 + 재인증 안내
만료 → 네이티브는 밀어 보내지 않음. 웹: remainingMs 종료 후 state.get → 네이티브: state(비활성, EXPIRED) → 웹: 버튼 비활성 + 재인증 안내 (B5)
지문 불일치 1회는 인증 창이 열린 채로 AUTH_FAILED가 되므로 authenticating=true와 함께 올 수 있습니다.
앱 이탈(화면 구성 변경 제외) → 네이티브: 활성 중이었거나 인증을 취소했으면 state(비활성, LEFT_APP)
앱 복귀 → 웹: state.get. 네이티브는 복귀 시 인증을 자동으로 다시 시작하지 않습니다
```

## 5. 결정 요약 (D-21·D-25)

2026-11-04 사용자가 설계 방향을 확정했습니다. 화면 진입 인증은 웹이 요청하고 창은 네이티브가 띄우는 것(B3), 네이티브 상태 기준, 신뢰 origin과 최상위 프레임 제한입니다. 당시 공개 계약 후보였던 전달 방식, 메시지 이름·필드, 오류 코드, origin 값은 2026-11-16 사용자 결정으로 v1 계약이 됐습니다.

| # | 질문 | 내용 |
| --- | --- | --- |
| B1 | 전달 방식 | 확정(v1): `addWebMessageListener` + origin 규칙 |
| B2 | 위 메시지 이름·필드 | 확정(v1): 이 문서의 3절(메시지 6종, `payment.check.result.reason` 값 집합, `v=1`·오류 규칙, 검사 순서, 크기 상한) |
| B3 | 화면 진입 시 인증을 누가 시작하는가 | 웹이 화면 진입 즉시 `auth.request`를 보내고, 인증 창은 네이티브가 띄웁니다. `state.get` 응답과 무관하게 보냅니다. PRD §4.3-1 "가상카드 화면을 열면 Android 네이티브 생체인증을 요청한다"를 "인증 창을 네이티브가 띄움"으로 읽는 해석입니다(D-21) |
| B4 | `payment.check`가 필요한가 | 필요합니다. 표시용 카운트다운과 실제 만료 사이의 차이를 막기 위해 |
| B5 | 상태 전송 빈도 | 상태 변화 시에만 전송, 남은 시간은 웹이 `remainingMs`로 계산해 표시. 만료는 밀어 보내지 않고, 웹이 `remainingMs`가 끝나면 `state.get`을 보냅니다. 최종 방어는 `payment.check`입니다 |
| B6 | 신뢰 origin | 확정(v1): debug `http://localhost:5173`, 테스트 origin은 테스트 코드가 주입(1절). release는 시작 주소 `https://beonepay.site/`, 신뢰 origin `https://beonepay.site` 하나(D-25 2026-11-16 보충) |

## 6. 구현 전 확인할 것

- `androidx.webkit` 안정판 버전과 `WebViewFeature.WEB_MESSAGE_LISTENER` 지원 확인(실기기 WebView 154에서는 PoC로 확인)
- 구현 테스트: 신뢰하지 않는 origin과 하위 프레임 메시지(`isMainFrame=false`)를 처리하지 않고 어떤 응답도 보내지 않는지(1절, 검사 순서 1단계)
- 구현 테스트: 모르는 `type`, 지원하지 않는 `v`, `v` 누락, 형식 오류가 인증 상태를 바꾸지 않고 해당 `code`의 `error`를 보내는지, 오류가 겹칠 때 검사 순서를 따르는지(3절)
- 구현 테스트(크기 경계): 길이 4096인 메시지는 크기 검사를 통과하고, 4097인 메시지는 JSON을 해석하지 않고 인증 상태를 바꾸지 않은 채 `INVALID_MESSAGE`를 받는지. 한글·보충 문자가 섞인 메시지도 UTF-16 코드 단위로 세는지
- release 설정 반영(별도 이슈): 현재 코드의 release `WEB_START_URL`은 빈 문자열이라 아무 페이지도 열지 않습니다. 반영할 때 시작 주소는 `https://beonepay.site/`이고, 신뢰 origin은 `https://beonepay.site` 하나여야 합니다(현재 `WebNavigationPolicy`는 시작 주소의 origin을 신뢰 origin으로 씁니다)
- release 빌드의 허용 origin 목록에 `www`·debug·테스트·`http://` origin과 와일드카드가 없는지

## 7. 계약 밖이거나 정하지 않은 것

- `WebViewFeature.WEB_MESSAGE_LISTENER`를 지원하지 않는 WebView에서 앱이 어떻게 동작할지는 이 계약에 없습니다.
- release 시작 주소의 경로(`/`)는 현재 결정입니다. 서버는 정적 확인 화면만 제공하므로(D-25, 사용자 전달) 앱 화면의 실제 시작 경로는 프런트 배포 뒤 바뀔 수 있습니다. origin `https://beonepay.site`는 그대로입니다.
- 실기기 확인(release 앱 설치·조작 포함)은 별도 승인을 받아야 하며, 이 문서 확정에 포함되지 않습니다.
- 인증 대기 120초·정리 대기 10초: `auth.request` 연결에는 기존 `AuthRequestGate`의 기본값(`AuthResponseTimeouts`)을 바꾸지 않고 **잠정 사용**합니다. **근거 없는 제안값이며 실기기 관측 전 미확정**입니다. 검증된 운영값이나 확정 정책이 아니고, 관측 근거로 다시 평가합니다(D-21, 2026-11-16 사용자 결정).
- WebView 플랫폼이 메시지를 네이티브 코드에 넘기기 전에 자체 크기 제한을 두는지는 확인하지 않았습니다. 크기 상한은 네이티브가 받은 뒤 검사합니다.
