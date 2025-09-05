-- 뷰 의존성 문제 해결 및 스키마 불일치 수정
-- V8에서 해결되지 않은 뷰 의존성 문제를 완전히 해결

-- 1. 모든 뷰와 의존성 제거 (CASCADE 사용)
DROP VIEW IF EXISTS v_account_migration_check CASCADE;

-- 2. 외래키 제약조건 임시 제거 (컬럼 타입 변경을 위해)
-- transaction_history의 외래키 제거
ALTER TABLE transaction_history DROP CONSTRAINT IF EXISTS transaction_history_account_number_fkey;

-- 3. users 테이블 컬럼 타입 수정
-- account_number: VARCHAR(20) -> VARCHAR(255)
ALTER TABLE users ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 4. accounts 테이블 컬럼 타입 수정
-- account_number: VARCHAR(20) -> VARCHAR(255)
ALTER TABLE accounts ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 5. account_balances 테이블 컬럼 타입 수정
-- account_number: VARCHAR(20) -> VARCHAR(255)
ALTER TABLE account_balances ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 6. transaction_history 테이블 컬럼 타입 수정
-- account_number: VARCHAR(20) -> VARCHAR(255)
ALTER TABLE transaction_history ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 7. virtual_accounts 테이블 컬럼 타입 수정
-- account_number: VARCHAR(20) -> VARCHAR(255)
ALTER TABLE virtual_accounts ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 8. user_accounts 테이블 컬럼 타입 수정 (V7에서 생성된 테이블)
-- account_number: VARCHAR(20) -> VARCHAR(255)
ALTER TABLE user_accounts ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 9. external_accounts 테이블 컬럼 타입 수정 (V7에서 생성된 테이블)
-- account_number: VARCHAR(50) -> VARCHAR(255)
ALTER TABLE external_accounts ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 10. payments 테이블 컬럼 타입 수정
-- payment_id: VARCHAR(50) -> VARCHAR(255)
ALTER TABLE payments ALTER COLUMN payment_id TYPE VARCHAR(255) USING payment_id::VARCHAR(255);
-- merchant_name: VARCHAR(100) -> VARCHAR(255)
ALTER TABLE payments ALTER COLUMN merchant_name TYPE VARCHAR(255) USING merchant_name::VARCHAR(255);
-- description: VARCHAR(255) -> TEXT
ALTER TABLE payments ALTER COLUMN description TYPE TEXT USING description::TEXT;

-- 11. transfers 테이블 컬럼 타입 수정
-- transfer_id: VARCHAR(50) -> VARCHAR(255)
ALTER TABLE transfers ALTER COLUMN transfer_id TYPE VARCHAR(255) USING transfer_id::VARCHAR(255);
-- description: VARCHAR(255) -> TEXT
ALTER TABLE transfers ALTER COLUMN description TYPE TEXT USING description::TEXT;

-- 12. login_history 테이블 컬럼 타입 수정
-- user_agent: VARCHAR(500) -> TEXT
ALTER TABLE login_history ALTER COLUMN user_agent TYPE TEXT USING user_agent::TEXT;

-- 13. refresh_tokens 테이블 컬럼 타입 수정
-- token: VARCHAR(500) -> TEXT
ALTER TABLE refresh_tokens ALTER COLUMN token TYPE TEXT USING token::TEXT;

-- 14. 외래키 제약조건 재생성
-- transaction_history의 외래키 재생성
ALTER TABLE transaction_history ADD CONSTRAINT transaction_history_account_number_fkey 
    FOREIGN KEY (account_number) REFERENCES accounts(account_number);

-- 15. 인덱스 재생성
DROP INDEX IF EXISTS idx_users_role;
CREATE INDEX idx_users_role ON users(role);
