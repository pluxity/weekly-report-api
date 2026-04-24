-- Teams(웹훅) 알림 발송 이력 테이블
-- 발송 시점에 PENDING 으로 기록하고, 발송 결과에 따라 SENT / FAILED 로 상태를 갱신한다

CREATE TABLE teams_notification_logs (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    type         VARCHAR(32)  NOT NULL,
    message      VARCHAR(2000) NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    fail_reason  VARCHAR(1000),
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255)
);

CREATE INDEX idx_teams_notification_logs_user_id_id
    ON teams_notification_logs (user_id, id DESC);
