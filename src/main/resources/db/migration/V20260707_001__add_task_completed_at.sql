-- tasks.completed_at: 실제 완료일 (지연 모델 개선 A안, pluxity/weekly-report#135). 미완료는 null.
-- 지연 계산의 완료 기준일이며, Epic/Project 완료일은 저장 없이 하위 Task에서 파생한다.
-- 완료일 입력 경로(승인/수정폼)는 후속 작업. 그 전까지 서비스단은 updated_at fallback으로 동작한다.
ALTER TABLE tasks ADD COLUMN completed_at DATE;

-- 기존 DONE 태스크 백필:
--   1순위: 승인 로그(APPROVE) 최신 시각 (실제 완료 시점에 가장 근접)
--   2순위: updated_at (승인 로그 도입 2026-04-08 이전 건 fallback)
UPDATE tasks t
SET completed_at = COALESCE(
    (
        SELECT MAX(l.created_at)::date
        FROM task_approval_logs l
        WHERE l.task_id = t.id
          AND l.action = 'APPROVE'
    ),
    t.updated_at::date
)
WHERE t.status = 'DONE'
  AND t.completed_at IS NULL;
