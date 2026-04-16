-- 1. User 테이블에 Teams 컬럼 추가
ALTER TABLE users
    ADD COLUMN aad_object_id         VARCHAR(255) UNIQUE,
    ADD COLUMN teams_conversation_id VARCHAR(255),
    ADD COLUMN teams_service_url     VARCHAR(255);

-- 2. 기존 TeamsAccount 데이터를 User로 백필
UPDATE users u
SET aad_object_id         = ta.aad_object_id,
    teams_conversation_id = ta.conversation_id,
    teams_service_url     = ta.service_url
FROM teams_accounts ta
WHERE u.id = ta.user_id;

-- 3. 테이블 제거
DROP TABLE IF EXISTS teams_accounts;
