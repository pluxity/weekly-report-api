-- User 엔티티에 @SoftDelete 적용을 위한 deleted 컬럼 추가
-- Hibernate @SoftDelete(columnName = "deleted") 기본값은 false

ALTER TABLE users ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
