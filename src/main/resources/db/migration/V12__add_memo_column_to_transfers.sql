-- transfers 테이블에 memo 컬럼 추가
-- 기존 description 컬럼이 있지만, 코드에서는 memo를 사용하므로 추가

-- memo 컬럼 추가 (TEXT 타입)
ALTER TABLE transfers ADD COLUMN IF NOT EXISTS memo TEXT;

-- 기존 description 데이터가 있다면 memo로 복사
UPDATE transfers SET memo = description WHERE memo IS NULL AND description IS NOT NULL;

-- description 컬럼은 유지 (기존 데이터 보존을 위해)
-- 필요시 나중에 제거할 수 있음
