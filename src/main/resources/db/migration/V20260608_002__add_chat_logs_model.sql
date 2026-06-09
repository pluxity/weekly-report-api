-- chat_logs 에 cost 산출 기준 모델 스냅샷 컬럼 추가 (단가 이력 추적용)
-- V20260608_001 은 이미 적용된 환경이 있어 수정하지 않고 신규 마이그레이션으로 분리

ALTER TABLE chat_logs ADD COLUMN model VARCHAR(128) NOT NULL DEFAULT '';
