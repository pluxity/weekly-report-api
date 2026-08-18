-- users.deleted (boolean) → users.retired_at (date)
--
-- 이 컬럼의 의미가 "삭제됨"에서 "퇴사함"으로 바뀐다. 완전 삭제는 DELETE /users/{id} 로
-- 분리됐고(V20260818_001 의 참조 규칙), 이 컬럼은 재직 상태만 나타낸다.
--
-- boolean + 시각을 따로 두지 않고 nullable 날짜 하나로 상태를 표현한다.
--   retired_at IS NULL  → 재직 중
--   retired_at NOT NULL → 그 날짜에 퇴사
-- 두 필드가 어긋나는 상태를 만들 수 없고, tasks.completed_at 과 같은 방식이다.
--
-- 기존 deleted = true 사용자의 실제 퇴사일은 알 수 없으므로 updated_at 으로 백필한다
-- (completed_at 백필과 동일한 fallback). 근사값이며 정확한 값이 아니다.
--
-- 배경: docs/notes/user-delete-retire-spec.md

ALTER TABLE users ADD COLUMN retired_at date;

UPDATE users SET retired_at = updated_at::date WHERE deleted = true;

ALTER TABLE users DROP COLUMN deleted;
