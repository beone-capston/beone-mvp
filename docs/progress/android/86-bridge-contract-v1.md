# #86 Android WebView 브리지 v1 계약 확정(문서)

- 이슈: [#86](https://github.com/beone-capston/beone-mvp/issues/86)
- 브랜치: `docs/86-bridge-contract-v1` (기준 `origin/develop` `5885454`)
- 문서: [브리지 계약](../../android/bridge-contract-candidate.md), [D-21](../../decisions.md#d-21-확정-정책)·[D-25](../../decisions.md#d-25-확정-정책)의 2026-11-16 보충

## 2026-11-16 · 브리지 v1 계약 문서 확정 · 완료(리뷰·병합 대기)
- 한 일: 2026-11-16 사용자 결정을 반영해 후보 계약 문서를 v1 확정 계약으로 바꿨습니다. 링크 유지를 위해 파일 이름은 그대로 두고 머리말 상태를 "확정(v1, 2026-11-16 사용자 결정)"으로 바꿨습니다. 메시지 6종, `payment.check.result.reason` 값 집합(= `state.reason` 집합), `v=1`·오류 규칙, 검사 순서(신뢰 origin·최상위 프레임 → 크기 → JSON → `v` → `type`), 웹→네이티브 4096 UTF-16 코드 단위 크기 상한, release 시작 주소 `https://beonepay.site/`와 신뢰 origin `https://beonepay.site`를 적었습니다. `docs/decisions.md` D-21·D-25에 2026-11-16 보충을 더하고 D-21 표 상태를 맞췄습니다. `docs/architecture.md`의 Android 한 줄을 고쳤습니다.
- 서비스에서 달라진 점: 없음. 코드와 설정은 바꾸지 않았습니다. release `WEB_START_URL`은 여전히 빈 문자열입니다.
- 검증: 공백 검사(`git diff --check`), 바뀐 문서의 제목 구조·상대 링크·앵커, 변경 파일이 `docs/`만인지, 문서 속 이름(`WebOrigin`, `WebNavigationPolicy`, `WEB_START_URL`, `InactiveReason`)을 코드와 대조했습니다. 문서만 바뀌어 Gradle은 실행하지 않았습니다.
- 남은 제한: 브리지 구현과 release 설정 반영은 별도 이슈입니다. 실기기 확인은 별도 승인입니다. `WEB_MESSAGE_LISTENER` 미지원 WebView 처리와 앱 화면의 실제 시작 경로는 정하지 않았습니다.
- 다음: 이 PR 병합 뒤 Android 브리지 로컬 구현(별도 이슈)
