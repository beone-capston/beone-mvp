# Android WebView 브리지 계약

> **상태: 사용자 결정 반영, 구현 전.** 2026-11-04 사용자 결정 [D-21](../decisions.md#d-21-확정-정책)을 반영했습니다. 파일 이름은 기존 링크를 유지하려고 그대로 둡니다. 브리지 코드는 아직 없고, 구현은 #24·#26 병합 뒤에 시작합니다([운영 규칙 5절](../development/autonomous-workflow.md)). 인증 대기 시간 값은 근거 없는 제안이며 실기기 관측으로 조정합니다.

근거: [PRD §4.3·FR-06](../PRD.md), [D-08](../decisions.md#d-08-확정-정책), [실기기 PoC](../d08-biometrics-poc.md)(PoC에서 `addWebMessageListener`로 성공·취소·불일치·잠금 상태 전달과 복귀 시 동기화를 확인).

## 1. 지키려는 원칙

- **네이티브가 기준입니다.** 60초 활성 여부와 남은 시간은 Android가 단조 시계로 판단합니다. 웹의 카운트다운은 표시용입니다. 웹이 "활성"이라고 주장해도 네이티브 상태를 바꾸지 않습니다.
- **필요한 값만 보냅니다.** 생체 정보, PIN, 시스템 오류 메시지 원문, 기기 식별자는 보내지 않습니다. 인증 수단은 `BIOMETRIC`·`DEVICE_CREDENTIAL` 구분까지만 보냅니다.
- **트리거일 뿐입니다.** 인증 결과는 앱 안의 가상 결제 화면을 여는 데만 씁니다. 웹이 이 값을 서버에 결제 승인 근거로 보내지 않고, 서버도 신뢰하지 않습니다(PRD FR-06).
- **신뢰 origin에만 노출합니다.** 허용 origin 목록은 빌드 변형별로 따로 둡니다. release는 앱 에셋 origin `https://appassets.androidplatform.net`만 허용합니다(D-21, release 에셋 구현은 2026-11-16 시연 뒤로 미룰 수 있음). debug 목록은 개발 서버 `http://localhost:5173`이고, 테스트 origin은 계측 테스트 코드가 주입하며 debug 목록에 넣지 않습니다. release 목록에는 debug·테스트 origin과 평문 HTTP(`http://`) origin을 넣지 않습니다. 모든 변형에 공통으로 쓰는 전역 목록은 두지 않습니다.
- **최상위 프레임만 처리합니다.** 허용 origin이라도 iframe 등 하위 프레임에서 온 메시지(`WebMessageListener`의 `isMainFrame`이 `false`)는 처리하지 않습니다. 인증 창을 띄우거나 상태를 바꾸지 않고, 그 프레임에 `state`·`error`를 포함한 어떤 응답도 보내지 않습니다(무시).

## 2. 전달 방식

| 후보 | 내용 | 평가 |
| --- | --- | --- |
| A (채택, D-21) | AndroidX WebKit `WebViewCompat.addWebMessageListener`에 허용 origin 규칙을 지정 | origin을 플랫폼이 검사합니다. PoC에서 동작을 확인했습니다. `androidx.webkit` 의존성과 WebView 기능 지원 확인이 필요합니다 |
| B | `addJavascriptInterface` | 모든 프레임·origin에 노출되기 쉬워 쓰지 않습니다 |

## 3. 메시지

모든 메시지는 JSON 문자열이고 `v`(계약 버전)와 `type`을 가집니다. 모르는 `type`이나 다른 `v`는 무시하고, 네이티브는 웹으로 `error`를 보냅니다.

### 웹 → 네이티브

| type | 필드 | 의미 |
| --- | --- | --- |
| `auth.request` | 없음 | 기기 인증 창을 띄워 달라는 요청. `state.get` 응답을 기다리지 않고 보낼 수 있습니다. 네이티브가 새 요청을 받을 수 없는 단계면 인증 창을 다시 띄우지 않고 현재 상태를 다시 보냅니다. 거부되거나 라이브러리에 전달되지 않은 경우에도 별도 메시지 없이 현재 상태를 다시 보냅니다. 활성 중 요청은 재인증으로 처리해 성공 시 60초를 새로 시작합니다(#22 구현) |
| `state.get` | 없음 | 현재 상태를 다시 보내 달라는 요청(화면 진입·복귀 시) |
| `payment.check` | 없음 | `가상 주문서 결제` 버튼을 누르기 직전, 지금 활성인지 네이티브에 다시 묻는 요청 |

### 네이티브 → 웹

| type | 필드 | 의미 |
| --- | --- | --- |
| `state` | `active: boolean`, `remainingMs: number`, `reason: string`, `method?: string`, `authenticating: boolean` | 현재 상태. 상태가 바뀔 때마다, 그리고 `state.get` 응답으로 보냅니다. `authenticating`: 네이티브가 새 인증 요청을 받을 수 없는 동안 `true`(인증 중·정리 중·정지 포함). `method`: 인증 수단을 알 수 없으면(`UNKNOWN`) 보내지 않습니다 |
| `payment.check.result` | `allowed: boolean`, `reason: string` | `payment.check`의 응답. `allowed=false`면 웹은 결제 요청을 보내지 않고 재인증을 안내합니다 |
| `error` | `code: string` | 잘못된 메시지 등 브리지 자체 오류 |

`reason` 값: `NOT_AUTHENTICATED`, `EXPIRED`, `AUTH_FAILED`, `AUTH_CANCELED`, `LEFT_APP`, `SETUP_REQUIRED`, `UNAVAILABLE`, `NO_RESPONSE`. 활성 상태면 `ACTIVE`. #22 구현의 `InactiveReason`과 같습니다. `NO_RESPONSE`는 인증 응답 대기 시간 초과(정지)이며, 웹은 "앱을 완전히 종료한 뒤 다시 열어 주세요"를 안내합니다. 네이티브가 자동으로 다시 인증하지 않습니다.

예시:

```json
{"v":1,"type":"state","active":true,"remainingMs":60000,"reason":"ACTIVE","method":"BIOMETRIC","authenticating":false}
```

```json
{"v":1,"type":"state","active":false,"remainingMs":0,"reason":"LEFT_APP","authenticating":false}
```

## 4. 흐름

```text
가상카드 화면 진입 → 인증 시작 (B3: 웹이 요청, 창은 네이티브)
  웹: auth.request (state.get 응답을 기다리지 않음)
→ 네이티브: 인증 창 (새 요청을 받을 수 없으면 생략하고 state만 전송) → 성공 → state(active, 60000)
→ 웹: 버튼 활성 + 표시용 카운트다운
→ 버튼 누름 → 웹: payment.check → 네이티브: payment.check.result(allowed)
→ 웹 → 서버: 모의 승인 요청 (서버는 인증 결과를 신뢰 근거로 쓰지 않음)

만료·실패·취소·앱 이탈 → 네이티브: state(비활성, reason) → 웹: 버튼 비활성 + 재인증 안내
지문 불일치 1회는 인증 창이 열린 채로 AUTH_FAILED가 되므로 authenticating=true와 함께 올 수 있습니다.
앱 이탈(화면 구성 변경 제외) → 네이티브: 활성 중이었거나 인증을 취소했으면 state(비활성, LEFT_APP)
앱 복귀 → 웹: state.get. 네이티브는 복귀 시 인증을 자동으로 다시 시작하지 않습니다
```

## 5. 결정 (D-21)

| # | 질문 | 결정 |
| --- | --- | --- |
| B1 | 전달 방식 | 후보 A(`addWebMessageListener` + origin 규칙) |
| B2 | 위 메시지 이름·필드 | 이 문서의 3절. 프런트엔드는 이 계약으로 구현합니다 |
| B3 | 화면 진입 시 인증을 누가 시작하는가 | 웹이 화면 진입 즉시 `auth.request`를 보내고, 인증 창은 네이티브가 띄웁니다. `state.get` 응답과 무관하게 보냅니다. PRD §4.3-1 "가상카드 화면을 열면 Android 네이티브 생체인증을 요청한다"를 "인증 창을 네이티브가 띄움"으로 읽는 해석입니다(D-21) |
| B4 | `payment.check`가 필요한가 | 필요합니다. 표시용 카운트다운과 실제 만료 사이의 차이를 막기 위해 |
| B5 | 상태 전송 빈도 | 상태 변화 시에만 전송, 남은 시간은 웹이 `remainingMs`로 계산해 표시. 만료는 밀어 보내지 않고, 웹이 `remainingMs`가 끝나면 `state.get`을 보냅니다. 최종 방어는 `payment.check`입니다 |
| B6 | 신뢰 origin | debug `http://localhost:5173`, release `https://appassets.androidplatform.net`, 테스트 origin은 테스트 코드가 주입(1절) |

## 6. 구현 전 확인할 것

- `androidx.webkit` 안정판 버전과 `WebViewFeature.WEB_MESSAGE_LISTENER` 지원 확인(실기기 WebView 154에서는 PoC로 확인)
- 하위 프레임 메시지를 `isMainFrame`으로 거르고 응답하지 않는지(1절)
- release 빌드의 허용 origin 목록에 debug·테스트·`http://` origin이 없는지
- 메시지 크기·형식 검증과 실패 시 `error` 응답
