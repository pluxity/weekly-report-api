-- 사용자 완전 삭제(DELETE /users/{id}) 시 참조 처리 규칙을 DB에 명시한다.
--
-- 기준: 그 참조가 "사용자의 소유물"인가, "조직의 자산"인가.
--   소유물(역할 배정/팀 소속/업무 그룹 배정) → 사용자와 함께 삭제 (CASCADE)
--   자산(태스크/승인 로그)                  → 레코드는 남기고 사용자 연결만 해제 (SET NULL)
--
-- 태스크는 프로젝트-업무 그룹 계층에 속하고 담당자는 그 속성일 뿐이므로,
-- 담당자가 삭제된다고 태스크가 사라지면 다른 사람의 업무 이력까지 훼손된다.
-- 승인 로그도 같은 이유로 "언제 무엇이 승인됐는지"는 남기고 수행자만 끊는다.
--
-- 퇴사(POST /users/{id}/retire)는 users.deleted 플래그만 바꾸므로 이 규칙과 무관하다.

-- 역할 배정: 사용자 소유물
ALTER TABLE user_role DROP CONSTRAINT fk_user_role_userid;
ALTER TABLE user_role
    ADD CONSTRAINT fk_user_role_userid
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- 팀 소속: 사용자 소유물
ALTER TABLE team_members DROP CONSTRAINT team_members_user_id_fkey;
ALTER TABLE team_members
    ADD CONSTRAINT team_members_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- 업무 그룹 배정: 사용자 소유물
-- (제약명 epic_assignments_assigned_by_fkey 는 실제 컬럼(user_id)과 어긋나지만,
--  이름 변경은 이 마이그레이션의 관심사가 아니므로 기존 이름을 유지한다)
ALTER TABLE epic_assignments DROP CONSTRAINT epic_assignments_assigned_by_fkey;
ALTER TABLE epic_assignments
    ADD CONSTRAINT epic_assignments_assigned_by_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- 태스크: 조직 자산 → 담당자 연결만 해제
ALTER TABLE tasks DROP CONSTRAINT tasks_assignee_id_fkey;
ALTER TABLE tasks
    ADD CONSTRAINT tasks_assignee_id_fkey
        FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE SET NULL;

-- 승인 로그: 태스크 이력 → 수행자 연결만 해제
-- SET NULL 을 걸려면 actor_id 의 NOT NULL 을 먼저 풀어야 한다.
ALTER TABLE task_approval_logs ALTER COLUMN actor_id DROP NOT NULL;
ALTER TABLE task_approval_logs DROP CONSTRAINT fk_task_approval_logs_actor;
ALTER TABLE task_approval_logs
    ADD CONSTRAINT fk_task_approval_logs_actor
        FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL;

-- PM / 팀 리더: 지금까지 FK 없는 생 bigint 였다.
-- ON DELETE 를 지정하지 않아 RESTRICT 로 둔다 — 책임자가 있는 채로 삭제되면
-- 프로젝트/팀이 담당자 없이 남으므로, 인계 후 삭제하도록 DB가 막는다.
-- (앱의 ensureNoActiveResponsibility 와 같은 규칙을 DB가 뒷받침한다)
-- 퇴사는 막지 않는다. 퇴사한 PM은 자리를 유지한 채 퇴사자로 표시된다.
ALTER TABLE projects
    ADD CONSTRAINT fk_projects_pm
        FOREIGN KEY (pm_id) REFERENCES users (id);

ALTER TABLE teams
    ADD CONSTRAINT fk_teams_leader
        FOREIGN KEY (leader_id) REFERENCES users (id);
