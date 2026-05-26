-- formatted/matched_against_prev를 jsonb → text로 전환.
-- Hibernate 내장 Jackson(2, Kotlin 모듈 없음)이 Kotlin data class 생성자를 역직렬화하지 못해,
-- 앱의 Jackson 3 ObjectMapper 기반 AttributeConverter로 (역)직렬화한다. jsonb 경로 쿼리는 사용하지 않음.

ALTER TABLE weekly_reports ALTER COLUMN formatted DROP DEFAULT;
ALTER TABLE weekly_reports ALTER COLUMN formatted TYPE text USING formatted::text;
ALTER TABLE weekly_reports ALTER COLUMN formatted SET DEFAULT '{}';

ALTER TABLE weekly_reports ALTER COLUMN matched_against_prev TYPE text USING matched_against_prev::text;
