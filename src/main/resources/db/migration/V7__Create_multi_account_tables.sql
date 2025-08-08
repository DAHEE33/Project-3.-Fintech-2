-- 사용자 계좌 테이블 (다중 계좌 지원)
CREATE TABLE user_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    account_name VARCHAR(50) NOT NULL,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    daily_limit DECIMAL(15,2) DEFAULT 10000000.00,
    monthly_limit DECIMAL(15,2) DEFAULT 100000000.00,
    version INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_accounts_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_user_accounts_balance CHECK (balance >= 0),
    CONSTRAINT chk_user_accounts_limits CHECK (daily_limit > 0 AND monthly_limit > 0)
);

-- 외부 계좌 테이블
CREATE TABLE external_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    bank_code VARCHAR(10) NOT NULL,
    bank_name VARCHAR(50) NOT NULL,
    account_holder_name VARCHAR(50),
    account_alias VARCHAR(50),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_verified_at TIMESTAMP NULL,
    verification_failure_count INTEGER DEFAULT 0,
    api_provider VARCHAR(50),
    external_account_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_external_accounts_user_id FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 인덱스 생성
CREATE INDEX idx_user_accounts_user_id ON user_accounts(user_id);
CREATE INDEX idx_user_accounts_account_number ON user_accounts(account_number);
CREATE INDEX idx_user_accounts_user_primary ON user_accounts(user_id, is_primary);
CREATE INDEX idx_user_accounts_status ON user_accounts(status);

CREATE INDEX idx_external_accounts_user_id ON external_accounts(user_id);
CREATE INDEX idx_external_accounts_bank_code ON external_accounts(bank_code);
CREATE INDEX idx_external_accounts_verification_status ON external_accounts(verification_status);
CREATE INDEX idx_external_accounts_is_active ON external_accounts(is_active);
CREATE INDEX idx_external_accounts_last_verified ON external_accounts(last_verified_at);

-- 기존 users 테이블의 계좌 데이터를 user_accounts 테이블로 마이그레이션
INSERT INTO user_accounts (user_id, account_number, account_name, balance, is_primary, created_at)
SELECT 
    u.id,
    u.account_number,
    CONCAT(u.name, '님의 기본계좌'),
    COALESCE(ab.balance, 0.00),
    TRUE,
    u.created_at
FROM users u
LEFT JOIN account_balances ab ON u.account_number = ab.account_number
WHERE u.account_number IS NOT NULL;

-- 기존 데이터 정합성 확인을 위한 뷰 생성 (임시)
CREATE OR REPLACE VIEW v_account_migration_check AS
SELECT 
    'users' as source_table,
    COUNT(*) as total_accounts,
    COUNT(CASE WHEN account_number IS NOT NULL THEN 1 END) as accounts_with_number
FROM users
UNION ALL
SELECT 
    'user_accounts' as source_table,
    COUNT(*) as total_accounts,
    COUNT(account_number) as accounts_with_number
FROM user_accounts;