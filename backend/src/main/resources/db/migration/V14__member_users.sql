-- 회원과 업무 장부 연결(결정 37 D1a·D2, G2). 회원 1명은 '내 카드'(REAL) 장부 1개를 가진다.
-- 기존 업무 테이블의 users(user_id) FK는 바꾸지 않는다. 시연 사용자 2행은 회원과 연결하지 않고 그대로 둔다.

-- 시연 행은 demo_user만, 회원 행은 member_id만 가진다. demo_user의 UNIQUE는 유지한다(NULL은 여러 개 허용).
-- 회원 삭제는 업무 데이터가 있으면 막는다(RESTRICT). 탈퇴·보존 정책(D10)은 미결이다.
ALTER TABLE users
    MODIFY COLUMN demo_user VARCHAR(32) NULL,
    ADD COLUMN member_id VARCHAR(36) NULL AFTER demo_user,
    ADD CONSTRAINT uk_users_member_id UNIQUE (member_id),
    ADD CONSTRAINT fk_users_member FOREIGN KEY (member_id) REFERENCES members (member_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_users_owner CHECK ((demo_user IS NULL) <> (member_id IS NULL));

-- 이미 이메일 인증을 마친 회원의 장부를 만든다. 이후 회원은 이메일 인증 완료와 같은 트랜잭션에서 만든다.
INSERT INTO users (demo_user, member_id, display_name, portfolio_type, read_only)
SELECT NULL, m.member_id, '내 카드', 'REAL', FALSE
FROM members m
WHERE m.email_verified_at IS NOT NULL;
