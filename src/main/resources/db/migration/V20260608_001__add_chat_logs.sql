-- chat 1턴의 LLM 파이프라인 기록 테이블 (디버깅용, append-only)
-- 성공/실패 모두 기록하며, 응답 흐름과 분리되어 저장 실패는 무시된다
-- 토큰은 단가가 다른 input/output 을 분리 저장하고 cost(USD)를 산출해 함께 기록
-- OpenRouter 외 provider 는 아직 사용량 미추출이라 토큰·cost 가 0 으로 남는다

CREATE TABLE chat_logs (
    id                   BIGSERIAL     PRIMARY KEY,
    user_id              BIGINT        NOT NULL,
    request_message      TEXT          NOT NULL,
    success              BOOLEAN       NOT NULL,
    intent_result        TEXT,
    action_result        TEXT,
    error_message        TEXT,
    intent_input_tokens  INTEGER       NOT NULL DEFAULT 0,
    intent_output_tokens INTEGER       NOT NULL DEFAULT 0,
    action_input_tokens  INTEGER       NOT NULL DEFAULT 0,
    action_output_tokens INTEGER       NOT NULL DEFAULT 0,
    cost                 NUMERIC(16,8) NOT NULL DEFAULT 0,
    created_at           TIMESTAMP     NOT NULL,
    updated_at           TIMESTAMP     NOT NULL,
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255)
);

CREATE INDEX idx_chat_logs_user_id_id
    ON chat_logs (user_id, id DESC);
