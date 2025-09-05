-- 누락된 컬럼들 추가 및 스키마 완성
-- 엔티티와 데이터베이스 스키마 간의 모든 누락된 컬럼들을 추가

-- 1. payments 테이블에 누락된 컬럼들 추가
-- account_number 컬럼 추가 (Payment 엔티티에서 사용)
ALTER TABLE payments ADD COLUMN IF NOT EXISTS account_number VARCHAR(255);
-- merchant_id 컬럼 추가
ALTER TABLE payments ADD COLUMN IF NOT EXISTS merchant_id VARCHAR(255);
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

-- 2. transfers 테이블에 누락된 컬럼들 추가
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

-- 3. login_history 테이블에 누락된 컬럼들 추가
-- phone_number 컬럼 추가
ALTER TABLE login_history ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);
-- result 컬럼 추가 (login_result -> result로 변경)
ALTER TABLE login_history ADD COLUMN IF NOT EXISTS result VARCHAR(20);
-- fail_reason 컬럼 추가
ALTER TABLE login_history ADD COLUMN IF NOT EXISTS fail_reason VARCHAR(255);
-- fail_count 컬럼 추가
ALTER TABLE login_history ADD COLUMN IF NOT EXISTS fail_count INTEGER DEFAULT 0;
-- is_locked 컬럼 추가
ALTER TABLE login_history ADD COLUMN IF NOT EXISTS is_locked BOOLEAN DEFAULT FALSE;

-- 4. users 테이블에 누락된 컬럼들 추가
-- loginFailCount, isLocked, lockExpiresAt, lockReason
ALTER TABLE users ADD COLUMN IF NOT EXISTS login_fail_count INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS lock_expires_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS lock_reason VARCHAR(255);

-- 5. users 테이블에 PIN 관련 컬럼들 추가
-- pinFailCount, isPinLocked, pinLockExpiresAt, pinLockReason, pinLastUsedAt
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_fail_count INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_pin_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_lock_expires_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_lock_reason VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS pin_last_used_at TIMESTAMP;

-- 6. accounts 테이블에 누락된 컬럼들 추가
-- status 컬럼 추가
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
-- version 컬럼 추가 (낙관적 락)
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;

-- 7. account_balances 테이블에 누락된 컬럼들 추가
-- version 컬럼 추가 (낙관적 락)
ALTER TABLE account_balances ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;

-- 8. transaction_history 테이블에 누락된 컬럼들 추가
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

-- 9. audit_logs 테이블 컬럼명 수정 (엔티티와 일치시키기)
-- event_result -> status로 변경
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS status VARCHAR(20);
-- 기존 데이터 복사
UPDATE audit_logs SET status = event_result WHERE status IS NULL;
-- event_result 컬럼 삭제 (선택사항, 기존 데이터 보존을 위해 주석 처리)
-- ALTER TABLE audit_logs DROP COLUMN IF EXISTS event_result;

-- user_id -> member_id로 변경 (엔티티와 일치)
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS member_id BIGINT;
-- 기존 데이터 복사
UPDATE audit_logs SET member_id = user_id WHERE member_id IS NULL;

-- 추가 필드들
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS event_description VARCHAR(255);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS request_data TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS response_data TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500);

-- 10. 데이터 정리 및 초기값 설정
-- login_history에서 login_result 데이터를 result로 복사
UPDATE login_history SET result = login_result WHERE result IS NULL;

-- 11. 인덱스 추가 (성능 최적화)
-- 자주 사용되는 컬럼들에 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_payments_account_number ON payments(account_number);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_transfers_sender_account ON transfers(sender_account_number);
CREATE INDEX IF NOT EXISTS idx_transfers_receiver_account ON transfers(receiver_account_number);
CREATE INDEX IF NOT EXISTS idx_transfers_status ON transfers(status);
CREATE INDEX IF NOT EXISTS idx_login_history_phone_number ON login_history(phone_number);
CREATE INDEX IF NOT EXISTS idx_login_history_result ON login_history(result);
CREATE INDEX IF NOT EXISTS idx_transaction_history_status ON transaction_history(status);
CREATE INDEX IF NOT EXISTS idx_users_is_locked ON users(is_locked);
CREATE INDEX IF NOT EXISTS idx_users_login_fail_count ON users(login_fail_count);
