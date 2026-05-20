-- 주간보고 도메인 신설
-- 팀 리더가 chat으로 작성한 주간보고를 저장·조회. 한 팀 × 한 주차 = 1건 (UNIQUE).
-- formatted/matched_against_prev는 LLM 결과 JSON 구조, Hibernate @JdbcTypeCode(SqlTypes.JSON)로 data class 직접 매핑.
-- team_id는 nullable (사업 단위 보고처럼 팀 매칭 실패 케이스 대비). team_name_raw는 LLM 추출 원본으로 항상 보존.

CREATE TABLE weekly_reports (
    id                    BIGSERIAL    PRIMARY KEY,
    team_id               BIGINT,
    team_name_raw         VARCHAR(255) NOT NULL,
    week_start            DATE         NOT NULL,
    week_label            VARCHAR(100),
    raw_content           TEXT         NOT NULL,
    formatted             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    matched_against_prev  JSONB,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    CONSTRAINT uk_weekly_reports_team_week UNIQUE (team_id, week_start),
    CONSTRAINT fk_weekly_reports_team FOREIGN KEY (team_id) REFERENCES teams (id)
);
