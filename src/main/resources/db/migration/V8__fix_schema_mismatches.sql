-- 전체 스키마 불일치 문제 해결
-- 엔티티와 데이터베이스 스키마 간의 모든 불일치를 수정

-- 1. v_account_migration_check 뷰 삭제 (임시 뷰이므로 제거)
DROP VIEW IF EXISTS v_account_migration_check;

-- 2. users 테이블 컬럼 타입 수정
-- account_number: VARCHAR(20) -> VARCHAR(255) (User 엔티티와 일치)
ALTER TABLE users ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 3. accounts 테이블 컬럼 타입 수정
-- account_number: VARCHAR(20) -> VARCHAR(255) (Account 엔티티와 일치)
ALTER TABLE accounts ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 4. account_balances 테이블 컬럼 타입 수정
-- account_number: VARCHAR(20) -> VARCHAR(255) (AccountBalance 엔티티와 일치)
ALTER TABLE account_balances ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 5. transaction_history 테이블 컬럼 타입 수정
-- account_number: VARCHAR(20) -> VARCHAR(255)
ALTER TABLE transaction_history ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 6. virtual_accounts 테이블 컬럼 타입 수정
-- account_number: VARCHAR(20) -> VARCHAR(255)
ALTER TABLE virtual_accounts ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 7. user_accounts 테이블 컬럼 타입 수정 (V7에서 생성된 테이블)
-- account_number: VARCHAR(20) -> VARCHAR(255)
ALTER TABLE user_accounts ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 8. payments 테이블 컬럼 타입 수정
-- payment_id: VARCHAR(50) -> VARCHAR(255) (기본값으로 확장)
ALTER TABLE payments ALTER COLUMN payment_id TYPE VARCHAR(255) USING payment_id::VARCHAR(255);
-- merchant_name: VARCHAR(100) -> VARCHAR(255)
ALTER TABLE payments ALTER COLUMN merchant_name TYPE VARCHAR(255) USING merchant_name::VARCHAR(255);
-- description: VARCHAR(255) -> TEXT (더 긴 설명을 위해)
ALTER TABLE payments ALTER COLUMN description TYPE TEXT USING description::TEXT;

-- 9. payments 테이블에 누락된 컬럼들 추가
-- account_number 컬럼 추가 (Payment 엔티티에서 사용)
ALTER TABLE payments ADD COLUMN IF NOT EXISTS account_number VARCHAR(255);
-- failed_reason 컬럼 추가
ALTER TABLE payments ADD COLUMN IF NOT EXISTS failed_reason TEXT;
-- pg_transaction_id 컬럼 추가
ALTER TABLE payments ADD COLUMN IF NOT EXISTS pg_transaction_id VARCHAR(255);
-- pg_response 컬럼 추가
ALTER TABLE payments ADD COLUMN IF NOT EXISTS pg_response TEXT;
-- approved_at, cancelled_at, refunded_at 컬럼 추가
ALTER TABLE payments ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMP;

-- 10. transfers 테이블 컬럼 타입 수정
-- transfer_id: VARCHAR(50) -> VARCHAR(255)
ALTER TABLE transfers ALTER COLUMN transfer_id TYPE VARCHAR(255) USING transfer_id::VARCHAR(255);
-- description: VARCHAR(255) -> TEXT
ALTER TABLE transfers ALTER COLUMN description TYPE TEXT USING description::TEXT;

-- 11. transfers 테이블에 누락된 컬럼들 추가
-- transaction_id 컬럼 추가 (Transfer 엔티티에서 사용)
ALTER TABLE transfers ADD COLUMN IF NOT EXISTS transaction_id VARCHAR(255) UNIQUE;
-- bank_transaction_id 컬럼 추가
ALTER TABLE transfers ADD COLUMN IF NOT EXISTS bank_transaction_id VARCHAR(255);
-- sender_account_number, receiver_account_number 컬럼 추가
ALTER TABLE transfers ADD COLUMN IF NOT EXISTS sender_account_number VARCHAR(255);
ALTER TABLE transfers ADD COLUMN IF NOT EXISTS receiver_account_number VARCHAR(255);
-- failed_reason 컬럼 추가
ALTER TABLE transfers ADD COLUMN IF NOT EXISTS failed_reason TEXT;
-- processed_at 컬럼 추가
ALTER TABLE transfers ADD COLUMN IF NOT EXISTS processed_at TIMESTAMP;

-- 12. login_history 테이블 컬럼 타입 수정
-- user_agent: VARCHAR(500) -> TEXT (더 긴 user agent를 위해)
ALTER TABLE login_history ALTER COLUMN user_agent TYPE TEXT USING user_agent::TEXT;

-- 13. login_history 테이블에 누락된 컬럼들 추가
-- phone_number 컬럼 추가
ALTER TABLE login_history ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);
-- result 컬럼 추가 (login_result -> result)
ALTER TABLE login_history ADD COLUMN IF NOT EXISTS result VARCHAR(20);
-- fail_reason 컬럼 추가
ALTER TABLE login_history ADD COLUMN IF NOT EXISTS fail_reason VARCHAR(255);
-- fail_count 컬럼 추가
ALTER TABLE login_history ADD COLUMN IF NOT EXISTS fail_count INTEGER DEFAULT 0;
-- is_locked 컬럼 추가
ALTER TABLE login_history ADD COLUMN IF NOT EXISTS is_locked BOOLEAN DEFAULT FALSE;

-- 14. refresh_tokens 테이블 컬럼 타입 수정
-- token: VARCHAR(500) -> TEXT (더 긴 토큰을 위해)
ALTER TABLE refresh_tokens ALTER COLUMN token TYPE TEXT USING token::TEXT;

-- 15. audit_logs 테이블 컬럼 타입 수정
-- details: TEXT -> TEXT (이미 TEXT이므로 변경 불필요)

-- 16. users 테이블에 누락된 컬럼들 추가
-- loginFailCount, isLocked, lockExpiresAt, lockReason
ALTER TABLE users ADD COLUMN IF NOT EXISTS login_fail_count INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS lock_expires_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS lock_reason VARCHAR(255);

-- 17. users 테이블에 PIN 관련 컬럼들 추가
-- pinFailCount, isPinLocked, pinLockExpiresAt, pinLockReason, pinLastUsedAt
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_fail_count INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_pin_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_lock_expires_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_lock_reason VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_last_used_at TIMESTAMP;

-- 18. accounts 테이블에 누락된 컬럼들 추가
-- status 컬럼 추가
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
-- version 컬럼 추가 (낙관적 락)
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;

-- 19. account_balances 테이블에 누락된 컬럼들 추가
-- version 컬럼 추가 (낙관적 락)
ALTER TABLE account_balances ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;

-- 20. transaction_history 테이블에 누락된 컬럼들 추가
-- balance_before 컬럼 추가
ALTER TABLE transaction_history ADD COLUMN IF NOT EXISTS balance_before DECIMAL(15,2);
-- reference_id 컬럼 추가
ALTER TABLE transaction_history ADD COLUMN IF NOT EXISTS reference_id VARCHAR(255);
-- transaction_id 컬럼 추가
ALTER TABLE transaction_history ADD COLUMN IF NOT EXISTS transaction_id VARCHAR(255);
-- created_by 컬럼 추가
ALTER TABLE transaction_history ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
-- status 컬럼 추가
ALTER TABLE transaction_history ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'COMPLETED';

-- 21. virtual_accounts 테이블에 누락된 컬럼들 추가
-- status 컬럼 추가 (이미 있지만 확인)
ALTER TABLE virtual_accounts ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';

-- 22. external_accounts 테이블에 누락된 컬럼들 추가 (V7에서 생성된 테이블)
-- account_number: VARCHAR(50) -> VARCHAR(255)로 확장
ALTER TABLE external_accounts ALTER COLUMN account_number TYPE VARCHAR(255) USING account_number::VARCHAR(255);

-- 23. 인덱스 재생성 (컬럼 타입 변경으로 인한 인덱스 재생성)
-- 기존 인덱스 삭제 후 재생성
DROP INDEX IF EXISTS idx_users_role;
CREATE INDEX idx_users_role ON users(role);

-- 24. 외래키 제약조건 확인 및 재생성 (필요시)
-- transaction_history의 외래키가 accounts 테이블을 참조하는지 확인
-- 만약 virtual_accounts를 참조해야 한다면 수정 필요