-- 주간보고는 항상 한 팀에 종속된다는 도메인 결정 반영 (이슈 #56 명세의 "nullable: 사업 단위 보고 대비"를 거스름)
-- LLM 매칭 실패 시 chat 라우터(#57)에서 작성 거부 + 재입력 요청으로 처리하기로 합의.

ALTER TABLE weekly_reports
    ALTER COLUMN team_id SET NOT NULL;
