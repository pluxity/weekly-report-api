-- Task 승인 워크플로우용 append-only 이력 테이블
-- 담당자의 리뷰 요청(REVIEW_REQUEST), PM 의 승인(APPROVE)/반려(REJECT) 액션을 순차 보존한다
-- TaskStatus.IN_REVIEW 값 추가는 VARCHAR(@Enumerated STRING) 컬럼이라 DDL 불필요

CREATE TABLE task_approval_logs (
    id          BIGSERIAL    PRIMARY KEY,
    task_id     BIGINT       NOT NULL,
    actor_id    BIGINT       NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    reason      VARCHAR(1000),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT fk_task_approval_logs_task
        FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_approval_logs_actor
        FOREIGN KEY (actor_id) REFERENCES users (id)
);