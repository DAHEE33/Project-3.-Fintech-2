-- transfer_id null 제약 문제 해결
-- transfer_id 컬럼을 nullable로 변경하거나 기본값 설정

-- 1. transfer_id 컬럼을 nullable로 변경 (임시 해결책)
ALTER TABLE transfers ALTER COLUMN transfer_id DROP NOT NULL;

-- 2. 기존 null 값들에 대해 기본값 설정
UPDATE transfers SET transfer_id = 'TXN' || LPAD(id::text, 10, '0') WHERE transfer_id IS NULL;

-- 3. transfer_id 컬럼을 다시 NOT NULL로 설정
ALTER TABLE transfers ALTER COLUMN transfer_id SET NOT NULL;

-- 4. transfer_id 컬럼 타입을 VARCHAR(255)로 확장 (V9에서 이미 변경되었지만 확인)
ALTER TABLE transfers ALTER COLUMN transfer_id TYPE VARCHAR(255) USING transfer_id::VARCHAR(255);

-- 5. description 컬럼을 TEXT로 변경 (V9에서 이미 변경되었지만 확인)
ALTER TABLE transfers ALTER COLUMN description TYPE TEXT USING description::TEXT;

-- 6. sender_account_number, receiver_account_number 컬럼 타입을 VARCHAR(255)로 확장
ALTER TABLE transfers ALTER COLUMN sender_account_number TYPE VARCHAR(255) USING sender_account_number::VARCHAR(255);
ALTER TABLE transfers ALTER COLUMN receiver_account_number TYPE VARCHAR(255) USING receiver_account_number::VARCHAR(255);

-- 7. bank_transaction_id 컬럼 타입을 VARCHAR(255)로 확장
ALTER TABLE transfers ALTER COLUMN bank_transaction_id TYPE VARCHAR(255) USING bank_transaction_id::VARCHAR(255);

-- 8. failed_reason 컬럼을 TEXT로 변경
ALTER TABLE transfers ALTER COLUMN failed_reason TYPE TEXT USING failed_reason::TEXT;

