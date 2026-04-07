-- users 테이블에서 department 컬럼 제거
-- 사용처가 없어 제거. 대시보드의 department 필드는 Team.name을 보여주는 별개 필드로 영향 없음.

ALTER TABLE users DROP COLUMN department;
