-- users 테이블에 email 컬럼 추가 + 기존 사용자 백필 + unique 제약
-- Teams 봇 인증을 username 매칭에서 email 매칭으로 전환하기 위함

ALTER TABLE users ADD COLUMN email VARCHAR(255);

UPDATE users
SET email = username || '@pluxity.com'
WHERE email IS NULL;

ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);
