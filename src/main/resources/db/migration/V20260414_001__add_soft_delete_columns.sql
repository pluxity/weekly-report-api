-- Project, Epic, Task 엔티티에 @SoftDelete 적용을 위한 deleted 컬럼 추가
-- Hibernate @SoftDelete(columnName = "deleted") 기본값은 false

ALTER TABLE projects ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE epics ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tasks ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
