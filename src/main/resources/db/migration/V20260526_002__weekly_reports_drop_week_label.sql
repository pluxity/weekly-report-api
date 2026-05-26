-- week_label 컬럼 제거. 주차 표기는 week_start(정규화된 월요일)로 갈음하고 별도 보존하지 않음.

ALTER TABLE weekly_reports DROP COLUMN week_label;
