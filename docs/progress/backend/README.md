# 백엔드 진행 상황

작성 2026-10-28, 갱신 2026-11-01·2026-11-14(병합 목록만)·2026-11-16(#80 병합 상태, #84). 문서 기준 `origin/develop` `f751c78`, #80 관련 문장은 `5885454`(PR #81 병합). 아래 검증 결과는 각 PR head에서 실행한 것이며, 이 기준 커밋에서 다시 실행하지 않았습니다.

## 지금 가능한 기능
- `develop` 반영: 카드 규칙·입출력 모델과 검증, 날짜에 맞는 검증 규칙 선택, 공통 API 응답·오류 처리, 서비스 한 건의 정률·정액 계산·대상 금액 상한·원 미만 발생 확인·최소 금액·결제 방식·업종 판정·전월 실적 구간 선택(#21), 서비스 하나의 후보 판정·최종 채택 여부·실제 기여액을 나눠 담는 결과 타입과 생성 검증(#29), 한도 없는 비선택형 서비스 하나의 주문 한 건 후보 금액(`ServiceCandidate`) 판정기(#31), 선택된 카드 규칙의 서비스들을 후보·확인 필요·지원 범위 밖으로 나눠 모으는 취합기(`CardServiceCandidateCollector`, #33), 아래 결정기(#35)·진입점(#38).
- 결정기(#35, `develop` 반영): 취합 결과로 카드 한 장·주문 한 건의 서비스별 최종 채택·기여액과 카드 금액(`OrderBenefit`)을 정합니다(`OrderBenefitDecider`). 한도 없는 비선택형 서비스만 판정하며, 확인 필요·지원 범위 밖 서비스, 중복 관계 미확정·중복 허용, 취소 귀속·할인 매출 실적 제외의 영향이 있으면 카드 금액을 `확인 필요`로 두고 구조화된 사유를 남깁니다. 실제 카드 4종은 한도·선택형 서비스가 있어 대부분 `확인 필요`입니다.
- 진입점(#38, `develop` 반영): 규칙 카탈로그·카드·판정일을 받아 규칙 선택부터 주문 혜택 결정까지 한 번에 호출합니다(`OrderBenefitResolver`). 판정일은 호출자 입력이며 주문일로 대신하지 않습니다.
- CODEF 응답 해석(#41, `develop` 반영): CODEF 카드 API(승인내역·보유카드·실적조회) 응답 본문에서 결과 코드 분류와 비민감 필드만 해석합니다(`CodefCardResponseParser`). 빈 값·형식 오류는 0원·0건으로 바꾸지 않고, 성공은 별첨 근거의 `CF-00000` 정확 일치일 때만이며 데이터 사용 가능을 뜻하지 않습니다. 호출·토큰·저장·상태값 변환은 없습니다. `CF-12401`·`CF-12834`는 공식 목록 설명에 맞춰 미분류로 정정했습니다(#47, `develop` 반영).
- 전송 단계(#43, `develop` 반영): HTTP 상태와 응답 본문을 받아 200은 UTF-8 URL 디코딩 뒤 위 해석기로, 401은 인증 오류, 그 밖은 서버·전송 오류로 나눕니다(`CodefProductResponseReader`, 공식 SDK 구현 근거이며 명세 문서 아님). 실제 호출·토큰·재시도는 없습니다.
- KB 취소 표현(#45, `develop` 반영): KB(`0301`) 승인내역 한 건이 공급자 취소 표현(type1 취소·type2 취소 행·정상 행) 중 어느 형태와 맞는지 기술합니다(`CodefCancellationFormDescriber`). 취소여부와 금액 모양을 함께 보고, 금액 계산·매칭·원장 반영은 하지 않습니다.
- 2026-11-03~04 `develop` 반영(아래 PR 목록): 규칙 로더, CODEF 전송 계층(실호출 없음), PR #66의 승인 범위 내 한도 잔여액 적용·택1·유예 판정, PR #68의 D-13 #35 8번 조건부 중복 합산, 엔진 입구 타입. 이 시점 `develop`에는 결과 연결 계산과 API·화면 연결이 없었습니다(결과 연결은 아래 #80).
- 연결 단계(2026-11-01 기준 문장): 규칙 선택 → 서비스 후보 취합 → 카드 한 장의 주문 혜택 결정까지 연결됐고(단일 진입점 포함, `develop`), 카드 간 비교(`CardBenefit`·`Recommendation`)·API·화면·앱에는 연결되지 않았습니다. CODEF 응답 해석(#41)·전송 단계(#43)·취소 표현 기술(#45)은 혜택 계산과 연결되지 않았습니다.
- 결과 연결(#80 → PR #81, `develop` 반영 `5885454`): 엔진 입구 구현체(`DefaultRecommendationEngine`)가 `EngineRequest`를 받아 `OrderBenefit`을 `CardBenefit`으로 연결하고(D-22), 카드별 평가(금액·카드 상태 집계·서비스 결과·주문 뒤 남은 한도·승인 시 차감액), 카드 간 비교·제외 사유, 조건부 추천(D-18 K2), D-12 선택, 대표 카드 고정 비교를 거쳐 `EngineResult`를 돌려줍니다. 계산 결과만 돌려주며 사용액을 저장·갱신하지 않습니다. HTTP·DB·화면·앱 연결과 배포는 없습니다.

## 아직 안 되는 기능
- 남은 작업: PR #66 범위 밖 선택형 서비스 판정, 공유 한도 배분과 승인 뒤 사용액·잔여 한도의 저장·갱신, 예정 소비 입력·저장과 배정 비교(D-18 K4), 모의 취소 되돌림, 할인 매출·취소 귀속 등 원장 근거 생성·자동 연결과 실적 원장 산출, 주문일·결제일에 따른 판정일 규칙, 월간 최적화. D-13 #35 8번 밖의 합산은 기존 범위 밖으로 구분합니다.
- 수동 카드·상태·주문 API, CODEF 호출·토큰·연결과 응답의 상태값 변환, 모의 승인.

## 현재 작업과 PR
- #21 → PR #23: `develop`에 병합 완료(`95992e5`). #29 → PR #30: `develop`에 병합 완료(`0ae6f55`).
- #31 → PR #32: `develop`에 병합 완료(`212c85b`). [작업 기록](31-service-candidate-evaluator.md) #33 → PR #34: `develop`에 병합 완료(`76198ec`). [작업 기록](33-card-service-candidates.md)
- #35 → PR #36: `develop`에 병합 완료(`06ca2b8`). [작업 기록](35-order-benefit-decider.md)
- #38 → PR #40: `develop`에 병합 완료(`053ebc1`). [작업 기록](38-order-benefit-entry.md)
- #41 → PR #42: `develop`에 병합 완료(`d9ae633`). [작업 기록](41-codef-card-response.md) #47 → PR #48: `develop`에 병합 완료(`cb9416f`). [작업 기록](47-codef-category-fix.md)
- #43 → PR #44: `develop`에 병합 완료(`2c1fe48`). [작업 기록](43-codef-product-transport.md) #45 → PR #46: `develop`에 병합 완료(`3bf359b`). [작업 기록](45-codef-kb-cancellation.md)
- 2026-11-03~04 병합: 규칙 데이터 #59 → PR #60(`f0e42d9`), 규칙 로더 #61 → PR #63(`62b6fe2`), CODEF 전송 계층 #62 → PR #64(`63cadc7`), 한도·택1·유예 #65 → PR #66(`182923e`), 중복 합산 #67 → PR #68(`a463f72`), 엔진 입구 타입 #69 → PR #71(`967f4ce`). 이 여섯 PR의 단계 기록 파일은 없고 각 PR 본문에 검증이 있습니다.
- #80 → PR #81: 엔진 입구 구현체와 결과 연결(D-22). `develop`에 병합 완료(`5885454`). [작업 기록](80-engine-result-link.md) 공유 문서 정합 #84: [작업 기록](84-pr81-shared-sync.md)
- #82 → PR #83: CODEF 2차 오프라인 변환 1단계(`codef.sync`: 대상별 판정·보유카드 후보·승인 행 분류·실적 참고값, 합성 응답만). `develop`에 병합 완료(`dc107da`). 실제 호출·HTTP/DB 연결은 없고, 카드 연결·짝짓기·후보 상품·승인 날짜 해석(R1~R4)은 이 1단계에 미적용. [작업 기록](82-codef-offline-adapter.md)
- #92 → PR #93: CODEF 2차 오프라인 변환 2단계(`codef.sync`: 카드명 정확 일치 카드 연결, 승인 날짜·시각 해석, 승인번호 지문 주입 기반 중복 키·재조회 대조, D-17 Type2 짝짓기와 불완전 사유 5종, 합성 응답·합성 테스트 키만). PR #93(`develop` 대상, 병합 전). HMAC 키 공급·회전 미결, R3 보류, 부품 구현이며 HTTP API 완성 아님. [작업 기록](92-codef-ledger-candidate.md)
- #100 → PR: 업무 API 공통 기반(`user` 패키지의 `CurrentUserResolver`·시연 사용자 헤더, Flyway `V2` 시연 사용자 시드, `local`·`demo` CORS)과 `GET /api/v1/demo-users`. `develop` 대상, 병합 전. `GET /api/v1/me`는 보유 카드 작업으로 옮김. [작업 기록](100-business-api-foundation.md)
- #108 → PR: 현재 사용자 `GET /api/v1/me`와 보유 카드 API 5개(`card` 패키지 보유 카드, Flyway `V4__user_cards.sql` 임시 번호, 가상 포트폴리오 시드 4장, 대표 카드는 확장 지점만·추천 무효화는 B2). `develop` 대상, 병합 전. [작업 기록](108-user-cards-api.md)
- #110 → PR: 카드 월간 상태 조회·수동 입력 API(`monthlystate` 패키지, Flyway `V5__monthly_states.sql` 임시 번호, 가상 포트폴리오 시드 없음 결정 27 A3, 스냅샷 뒤 모의 승인 합산은 B3). `develop` 대상, 병합 전. [작업 기록](110-monthly-states-api.md)
- #105 → PR #106: 카드 상품 목록·상세 API(`card` 패키지 상품 조회, `RuleCatalog` 읽기, 테이블·Flyway 없음, `RuleCatalog.cardIds()` 추가). `develop` 병합(`cda31ca`). [작업 기록](105-card-products-api.md)
- #104 → PR: 주문 생성·조회와 예정 소비 API(`order` 패키지, Flyway `V3`(#105 병합 뒤 번호 확인), 예정 소비는 저장만 D-18 K4). `develop` 대상, 병합 전. [작업 기록](104-orders-planned-spends-api.md)
- #111 → PR: 결제 전 설정 조회·변경 API와 대표 카드 보호(`preference` 패키지, Flyway `V6__payment_preferences.sql` 임시 번호, card는 409 연결 최소 변경, 추천·모의 승인 사용은 B2·B3). `develop` 대상, 병합 전. [작업 기록](111-payment-preference-api.md)
- #114 → PR: 추천 계산·조회 API(`recommendation` 패키지, 엔진 입구만 호출, Flyway `V7__recommendations.sql` 임시 번호, 조회 때 지연 무효화, card·order 쪽 추천 연결과 모의 승인 연동은 후속). `develop` 대상, 병합 전. [작업 기록](114-recommendations-api.md)
- #116 → PR: 모의 승인·조회·취소 API(`payment` 패키지, `ledger`는 쓰기만, 주문 `PAID`·`CANCELLED` 최소 전이, recommendation 공개 메서드 1개 추가, Flyway `V8__simulated_payments_ledger.sql` 임시 번호, `Idempotency-Key`, 스냅샷 뒤 합산·추천 ID 연결은 B2f). `develop`에 병합 완료(`ca60dda`). [작업 기록](116-simulated-payments-api.md)
- #118 → PR: 모의 원장 조회 API(`ledger` 패키지 조회 쪽, 거래일 기준 달, 1차 CODEF coverage는 `complete=false`·사유 없음, Flyway 없음, 페이지 나눔 없음). `develop`에 병합 완료(`9d26194`). [작업 기록](118-ledger-transactions-api.md)
- #120 → PR: 추천 무효화·최근 추천·스냅샷 뒤 사용액 연결(새 API 없음, recommendation·monthlystate→ledger 읽기, card·order는 받는 쪽 조회 포트·구현 recommendation, 확인 필요 버킷은 엔진 입구 `NOT_ENTERED`(사유 추가 후보), Flyway 없음). `develop`에 병합 완료(`c02409c`). [작업 기록](120-recommendation-usage-links.md)
- #122 → PR: 같은 카드 동시 모의 승인 한도 경합 방지(승인이 `CREATED` 주문이면 주문 행 다음 보유 카드 행 전부를 ID 오름차순 잠금, 첫 일반 읽기 전, 재생은 잠금 없음, card 잠금 메서드 1개, 취소는 카드 잠금 없음, 새 API·Flyway 없음). `develop`에 병합 완료(`c3ce45e`). [작업 기록](122-simulated-payment-card-lock.md)
- #124 → PR: 회원 인증 U1 세션·CSRF 기반과 `GET /api/v1/auth/csrf`(`auth` 패키지, Flyway `V9__auth_sessions.sql` 임시 번호, `local`·`demo` 전용, Spring Security 없음, 익명 세션 미사용 2시간·절대 상한 없음·사용마다 쿠키 갱신(사용자 결정), 계약 `docs/api/auth.md`는 Notion 인증 명세 사본 반영). `develop` 대상, 병합 전. [작업 기록](124-auth-session-csrf.md)
- #126 → PR: CODEF 연결 상태 조회·계정 연결·해제 API(C2, `codef.application`·`persistence`·`web` 신규, 게이트웨이 인터페이스와 local·demo 가짜 구현만, 실 CODEF·실키 없음, connectedId AES-GCM 암호화·키 환경 변수, 해제는 연결 행만 삭제, Flyway `V10__codef_connections.sql` 임시 번호). `develop`에 병합 완료(`6f9a4c9`). [작업 기록](126-codef-connection.md)
- #128 → PR: 회원 인증 U2 회원가입·인증메일 재요청·이메일 인증 완료(`auth` 패키지, Flyway `V11__auth_members.sql` 임시 번호, `members`(ID `usr-`+32자)·`auth_tokens`(해시만)·`auth_mail_throttles`(이메일 해시), Argon2id m=19456·t=2·p=1과 `bcprov-jdk18on` 추가, 가짜 메일 발송기, 같은 주소 60초 합산 429, 계정 존재 비공개, 링크 형식·정리 기한 미정). `develop` 대상, 병합 전. [작업 기록](128-auth-sign-up.md)
- #129 → PR: CODEF 조회·동기화 API(C3, `POST /api/v1/me/codef/syncs`, 가짜 게이트웨이·합성 fixture만, 새 CODEF 카드 `UNMAPPED`, 승인 행은 codef 전용 테이블·원장 불변, 승인번호 HMAC 지문만·키 환경 변수, 실적은 참고값, card 메서드 2개 추가, Flyway `V12__codef_syncs.sql` 임시 번호). `develop`에 병합 완료(`876b515`). [작업 기록](129-codef-syncs.md)
- #132 → PR: 회원 인증 U3 로그인·로그아웃·현재 회원 세션 조회(`auth` 패키지, Flyway `V13__auth_member_sessions.sql` 임시 번호(`V12`는 CODEF C3 #129), `auth_sessions.member_id`, 회원 세션 미사용 7일·최대 30일·동시 5개(6번째는 가장 오래 쓰지 않은 세션 종료), 로그인 때 세션 ID·CSRF 교체와 이전 세션 폐기(병합 조건 ④ 통합 테스트), 같은 이메일 15분 5회 실패 시 15분 429(IP 기준 없음), 계정 없음도 더미 해시 비교, 로그인 READ COMMITTED). `develop` 대상, 병합 전. [작업 기록](132-auth-login.md)
- #134 → PR: 월간 상태 출처 충돌 계산 기준 선택 API(C1, `PUT .../monthly-states/{month}/source-selections`, 관리자 판단 P10: 요청 검증 400·카드 404·충돌이 없으면 항상 409 `SOURCE_CONFLICT_NOT_FOUND`, D-16상 지금은 충돌 경로가 없음, 가상 포트폴리오도 같은 판정, Flyway·테이블 없음). `develop`에 병합 완료(`94f655a`). [작업 기록](134-source-selections.md)
- #136 → PR: F1(`GET /api/v1/me` `codefConnected`를 연결 행 존재로 반영, card 조회 포트 `CodefConnectionLookup`·codef 구현, README에 CODEF 키 이름, `.env.example`은 가드레일로 후속, `DemoUserIntegrationTest` 간헐 실패 조사 기록만). `develop` 대상, 병합 전. [작업 기록](136-codef-connected.md)
- #138 → PR: 회원 인증 U4 비밀번호 재설정 요청·완료(`auth` 패키지, 새 Flyway 없음, 재설정 토큰 15분·1회용 해시 저장, 메일 60초 합산, 완료 시 그 회원의 모든 세션 종료, 관리자 판단: 미인증 계정에도 발송·로그인 잠금 유지, [기록](138-auth-password-reset.md))
- #141 → PR: 업무·인증 API 구현 상태 문구 정합(문서만, S1A R1~R8: `docs/api/README.md`·개별 문서 5행·`docs/api.md` 세 곳·`docs/domains/ledger.md` CODEF 완전성, 정책 변경 없음, [기록](141-api-status-sync.md))
- #151 → PR: 엔진 입구 사유 `POST_SNAPSHOT_ACTIVITY_UNRESOLVED` 추가(결정 32 Q6, 스냅샷 뒤 모르는 차감액·취소로 확인 필요가 된 입력값만, 진짜 미입력은 `NOT_ENTERED`, 금액·판정·응답 불변, Flyway 없음). `develop` 대상, 병합 전. [작업 기록](151-engine-unknown-reason.md)
- #153 → PR: 결정 32 기존 동작 명시(문서만, S1A Q1~Q5·Q7~Q9·R9: `docs/api/` codef·recommendations·mock-payments·ledger·monthly-states·auth·cards, 정책·코드 변경 없음, [기록](153-decision32-contract-notes.md))
- #157 → PR: G2 첫 수직 기능(결정 37). 로컬 회원 프로필 `member`에서 회원 세션 → 회원 → '내 카드' 장부로 현재 사용자를 정하고 `GET /api/v1/me`·보유 카드 API를 회원별로 연결(Flyway `V14` `users.member_id`·백필, `CurrentUser` readOnly 기준, 401 → CSRF 403 → 400 → 404, 업무 요청 7일 연장, 프로필 동시 사용 시 기동 실패, 나머지 업무 API는 `member`에 미등록). `develop` 대상, 병합 전. [기록](157-member-session-mycards.md)
- #159 → 커밋·push·PR #162(#158은 develop 병합됨): G3a(결정 36·37). 로컬 회원 프로필 `member`에 월간 상태 3개(출처 충돌 선택 C1 포함)·결제 전 설정 2개를 등록(컨트롤러 `@Profile`만 변경, 서비스·DB 불변, C1 409·미저장 유지, 결제 설정 변경은 추천 무효화 없음). #158 브랜치 위에 쌓은 작업. [기록](159-member-monthly-preference.md)
- #160 → 커밋·push(#158은 develop 병합됨, PR은 G3a PR #162 병합 뒤): G3b(결정 36·37). 로컬 회원 프로필 `member`에 주문 2개·예정 소비 2개·추천 2개를 등록(컨트롤러 `@Profile`만 변경, 서비스·DB 불변, 결정 32 Q2·Q3를 `member` API로 확인, `GET /me/orders` 405). #159 브랜치 위에 쌓은 작업. [기록](160-member-order-recommendation.md)
- #165 → `develop` 동기화 뒤 PR: G3c 분리(결정 37). 로컬 회원 프로필 `member`에 원장 조회 1개만 등록(컨트롤러 `@Profile`만 변경, 서비스·DB 불변). 모의 결제 3개는 N-G3-2·D8 결정 대기로 미등록이며 `feat/161`에 보존. #160 브랜치 위에 쌓은 작업. [기록](165-member-ledger.md)
- #169 → 커밋·push(PR은 #168 병합 뒤): G4 B1·B2(결정 36·37). `docs/api.md` 회원 세션 절에 웹 클라이언트 호출 규칙(쿠키 포함·쓰기 CSRF 토큰·시연 헤더 무시·401 재로그인/403 토큰 재발급·no-store·결제 미등록)을 현재 구현 그대로 정리하고, 같은 origin 구성은 N-G4-ORIGIN·N-FE-PROXY 결정 대기로 표시. `VIRTUAL_PORTFOLIO` 헤더 주문이 회원 '내 카드' 장부에 저장되는 회귀 테스트 1건(코드·DB 불변). #165 브랜치 위에 쌓은 작업. [기록](169-member-web-client-rules.md)
- #183 → draft PR: G5 L1 공개 프로필 골격(결정 38-6·7). `spring.profiles.group.prod: member`로 회원 부품 재사용, `application-prod.yml`(전달 헤더 미해석·flyway clean 비활성, 비밀값 없음), `PublicProfileGuard`(`prod`+`local`·`demo` 또는 `member` 없는 `prod`는 기동 실패). prod는 실메일 발송기(N1 결정 대기) 없이는 공개 배포 불가이며 Fake 메일 차단(L2)·compose(L8)는 후속. [기록](183-prod-profile-skeleton.md)
- #186 → draft PR: G5 L4·L5 prod 웹 슬라이스 고정(결정 38-7·8, 테스트·문서만, PR #185 위). prod에서 CODEF 4경로 404·컨트롤러·가짜 게이트웨이 Bean 없음(`CodefPublicProfileWebTest`), `X-Forwarded-*`를 보내도 쿠키·본문·`Location` 불변과 `ForwardedHeaderFilter` 없음(`ForwardedHeadersPublicWebTest`). `docs/domains/codef.md` 공개(prod) 범위 절. 공개 전환은 별도 승인. [기록](186-prod-codef-forwarded-guards.md)

## 사용자 확인 사항
1. 음수 전월 실적의 구간 판정은 D-17로 정했습니다(2026-11-01, 약관 근거 없음). 판정기는 구현 전까지 계속 거부합니다.
2. 정액 혜택이 결제 금액보다 클 때 결제 금액으로 줄일지. 권장안: 청구 할인은 결제 금액을 넘지 않게 줄이되 약관 근거를 함께 확인. 확정 전까지 산식(`BenefitArithmetic`)은 줄이지 않고, 후보 판정기(#31)는 2026-10-29 사용자 확인에 따라 `확인 필요`로 둡니다(D-13).
3. 원 미만 처리는 D-03 절사로 정했습니다(2026-11-03 사용자 결정, 약관 근거 없음). 코드는 구현 전이라 지금은 원 미만이 생기면 확인 필요입니다.
4. 업무 API 범위·공통 원칙은 D-09·D-14로 확정했고 1차 명세는 [`docs/api/`](../../api/README.md)에 있습니다(#55, 구현 전). 규칙 데이터 결정 전이라 API 구현을 시작하지 않았습니다.
5. CODEF 카드 API 공식 문서는 Aside의 브라우저로 렌더링해 수집한 본문 사본(2026-10-31)으로 응답 해석만 구현했습니다(#41). 이 세션 도구로는 본문을 JavaScript로 그리는 공식 페이지를 직접 읽지 못했습니다(도구 접근 한계이며 명세 부재가 아님). 성공 코드 `CF-00000`은 별첨(2026-11-01 관찰)으로 확인했고, 공식 오류 목록 대조로 `CF-12401`·`CF-12834` 오분류를 정정했습니다(#47, 사용자 결정 2026-11-01). 전송 단계(#43)는 공식 SDK `easycodef-java-v2` 커밋 `721f566` 구현(명세 문서 아님)을 근거로 응답 쪽만 만들었습니다. #45에서 Aside가 수집한 공식 카드 시트·오류 전체 목록(2026-11-01 관찰, 실제 응답 관측 없음)으로 KB 조건을 정리했습니다. 미확인: 공식 명세 기준의 HTTP method·헤더·인코딩(SDK 구현으로만 확인), Content-Type·시간 초과·재시도, OAuth 발급·갱신의 서버 계약, `CF-12872` 의미(공식 목록 340행에 추가 설명 없음), 기관별 필수조건 시트, 부분취소 상세, KB 외 기관의 취소 표현, 실제 응답과 시트의 일치, 오류 각 코드의 상세 조치. 권장안: 취소 상세를 확인해 주시면 취소 금액 반영을, 전송 계약을 확인해 주시면 호출 계층을 이어 갑니다. 실적 기간·충족 여부의 상태값 변환은 D-04 결정이 필요합니다.
6. `currentOrderBenefit`·기여액 규칙, 카드 사유 순서, 한도 지원 범위, 조건부 추천은 D-18로, `CardBenefit` 전체 필드 매핑과 차감 표시는 D-22로 정했습니다. 공유 한도 배분은 미결입니다.
7. 판정일 산정은 D-23으로 정했습니다(2026-11-04). 주문일을 쓰고, 기준일이 승인일이 아닌 카드는 `확인 필요`로 두지 않고 안내에 표시합니다. 약관 근거는 없습니다.

## 다음 작업
- 2026-11-01의 "새 기능 구현 정지" 결정 뒤 2026-11-03부터 사용자 승인 단위로 구현이 이어져 위 PR이 병합됐습니다. 이 정정은 장기 연속 개발 모드나 업무 API 개발 재개를 승인하지 않습니다. 업무 API는 팀원 담당입니다(2026-11-04 사용자 결정). 결과 연결 PR 2는 이 문장을 쓴 당시에는 착수 승인 전이었고, 2026-11-16 승인으로 #80 → PR #81이 됐고 `develop`에 병합됐습니다(`5885454`).
- 후속 필수: D-18 K4 예정 소비 입력·저장(업무 API). K1 한도 판정은 PR #66, K2 조건부 추천·K3 결과 규칙과 K4의 "월간 합계 = 현재 주문" 계산은 `develop`에 반영됐습니다(PR #81, `5885454`).
- 대기 중: 업무 API(명세 필요), CODEF 실제 호출·토큰(키 필요, 요청 쪽 금지 입력 확인)·상태값 변환(D-04), 선택형 서비스·공유 한도 배분(정책 결정 필요). `CardBenefit` 전체 필드 매핑은 D-22로 정했고 `develop`에 반영됐습니다(PR #81, `5885454`).