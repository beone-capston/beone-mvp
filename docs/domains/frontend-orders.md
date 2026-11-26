# 프런트엔드 가상 주문

모의결제 탭에서 주문을 생성하고, 생성된 주문 중 결제 대기 상태인 주문을 빠른결제 화면으로 전달합니다.

- [OrderWorkspace](../../frontend/src/orders/OrderWorkspace.tsx)는 포트폴리오 모드별로 마운트되어 작성 중인 양식과 이번 실행에서 생성한 주문을 메모리에 유지합니다.
- [OrderForm](../../frontend/src/orders/OrderForm.tsx)과 [MerchantSheet](../../frontend/src/orders/MerchantSheet.tsx)는 주문 입력과 분류 선택을 담당합니다. [분류 목록](../../frontend/src/orders/merchantClasses.ts)은 백엔드 허용 코드와 화면 표시명을 연결합니다.
- [주문 모델](../../frontend/src/orders/order.ts)은 입력을 검증하고 전송 형식으로 바꿉니다. 날짜와 시각은 서울 기준의 입력 문자열을 유지하며 시각이 비어 있으면 `null`로 전송합니다.
- [주문 API](../../frontend/src/orders/orderApi.ts)는 생성과 단건 조회를 호출합니다. `VITE_API_BASE_URL`이 없으면 Vite 개발 환경에서 `http://localhost:8080`을 사용하고, 배포 빌드에서는 페이지와 같은 출처를 사용합니다. `local`·`demo` 프로필의 `X-BeONE-Demo-User` 헤더를 쓰고 쿠키는 보내지 않습니다.
- 빠른결제 이동 전 서버에서 주문 상태를 다시 확인합니다. `CREATED` 주문을 선택하면 [App](../../frontend/src/App.tsx)이 해당 모드의 선택 주문을 보관하고 빠른결제 화면에 요약을 표시합니다. 기기 인증과 모의 승인은 사용자의 별도 조작 흐름에 속하며, 모의 승인 API 연동은 후속 범위입니다.

목록 조회 API가 없어 새로고침 후 이전 주문은 복구할 수 없습니다. 현재 업무 API의 시연 사용자 구분은 회원 세션과 별개입니다. 프런트 테스트는 분류 코드 집합, 요청 형식, 화면 상태와 모드 분리를 검증합니다.

관련 명세는 [주문 API](../api/orders.md)와 [공통 응답 및 시연 사용자 규약](../api.md)을 참고합니다.
