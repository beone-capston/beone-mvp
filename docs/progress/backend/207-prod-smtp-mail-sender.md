# #207 prod SMTP 인증 메일 발송기 (N1, 스텁 SMTP 시험만)

브랜치 `feat/207-prod-smtp-mail-sender`, 시작 기준 `origin/develop` `7f3e49d`(#192 병합).

## 2026-11-25 · SMTP 발송기와 prod 기동 조건

- 근거: 결정 39-6(프로젝트 Gmail 앱 비밀번호, 서버 `.env`에만 보관, prod 실메일 발송 구현 PR, 계정·앱 비밀번호는 사용자 제공 대기), 결정 38-6(prod에서 가짜 발송기 차단, #189).
- 한 일
  - `spring-boot-starter-mail`(Spring Boot 플러그인 관리 버전 4.1.1, Jakarta Mail 2.1.5·Angus Mail 2.0.5)을 추가했습니다.
  - `SmtpAuthMailSender`: 목적별 제목·평문 본문·링크(`<기준 주소>/verify-email#token=…`, `/reset-password#token=…`)를 만들어 보냅니다. 실패는 던지지 않고 재시도하지 않으며, 로그는 목적·마스킹 수신자·예외 클래스 이름만 남깁니다.
  - `SmtpMailConfiguration`(`@Profile("prod")`): 필수 설정 6개(`spring.mail.host`·`port`·`username`·`password`, `beone.mail.from`, `beone.mail.link-base-url`) 중 빠진 키 이름을 모아 기동을 멈춥니다. 기준 주소는 `https://`만 받습니다.
  - `application-prod.yml`: SMTP 인증·STARTTLS 필수, 연결·읽기·쓰기 시간 제한 10초(제안값), mail health 끔. 호스트·포트·계정·비밀번호는 넣지 않았습니다.
  - #189의 "prod는 메일 빈이 없어 기동 실패" 시험·문서를 "설정이 있으면 SMTP 발송기로 기동, 없으면 키 이름과 함께 실패"로 바꿨습니다(`AuthMailSenderProfileTest`, `PublicProfileWebTest`, `PublicProfileIntegrationTest`, 문서).
  - 시험용 루프백 SMTP 서버 `SmtpStubServer`(127.0.0.1, EHLO·AUTH PLAIN·MAIL·RCPT·DATA만, STARTTLS 미지원)를 두었습니다. 새 의존성(GreenMail)은 넣지 않았습니다. 스텁이 STARTTLS를 지원하지 않아 시험 설정에서만 STARTTLS를 끕니다.
  - `PublicProfileGuard`의 CODEF 변수 차단은 그대로 두고 설명 문장 하나만 고쳤습니다.
- 실패 처리 판단: 기존 계약(가입·재요청·재설정 요청은 실제 발송 여부와 관계없이 같은 202, 202는 도착 보장 아님, 메일은 토큰 커밋 뒤)을 그대로 따랐습니다. 계약 변경은 없습니다. 발송이 요청 스레드에서 동기로 일어나 SMTP가 느리면 응답이 최대 시간 제한만큼 늦어질 수 있습니다(비동기화는 하지 않음).
- 결정 필요
  - N2: 링크 기준 주소 값. 키 `beone.mail.link-base-url`과 권장안(Host 헤더 미사용, 공개 HTTPS origin)만 두었습니다.
  - 결정 28: 링크의 화면 경로(`/verify-email`·`/reset-password`)와 토큰 위치(`#token=`)는 임시값입니다. frontend에 이 화면이 없어 실제 링크를 열어 인증하려면 frontend 작업이 필요합니다(B 담당, 이 PR에서 수정하지 않음).
- 하지 않은 것: 실제 Gmail 접속·발송, 앱 비밀번호·키·`.env` 열람, 서버 접속, Compose에 prod 프로필·메일 키 전달, frontend 수정, 재시도·비동기 발송.
- 검증: 아래 PR 본문 기록.
