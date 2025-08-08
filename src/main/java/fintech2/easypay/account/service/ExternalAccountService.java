package fintech2.easypay.account.service;

import fintech2.easypay.account.entity.ExternalAccount;
import fintech2.easypay.account.repository.ExternalAccountRepository;
import fintech2.easypay.external.service.ExternalBankApiService;
import fintech2.easypay.external.dto.AccountVerificationRequest;
import fintech2.easypay.external.dto.AccountVerificationResponse;
import fintech2.easypay.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 외부 계좌 연동 관리 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalAccountService {
    
    private final ExternalAccountRepository externalAccountRepository;
    private final ExternalBankApiService externalBankApiService;
    private final AuditLogService auditLogService;
    
    private static final int MAX_EXTERNAL_ACCOUNTS_PER_USER = 10; // 사용자당 최대 외부 계좌 수
    
    /**
     * 사용자의 모든 외부 계좌 조회
     */
    public List<ExternalAccount> getUserExternalAccounts(Long userId) {
        log.debug("사용자 외부 계좌 목록 조회: userId={}", userId);
        
        List<ExternalAccount> accounts = externalAccountRepository
                .findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId);
        
        log.info("사용자 외부 계좌 목록 조회 완료: userId={}, 계좌수={}", userId, accounts.size());
        return accounts;
    }
    
    /**
     * 인증된 외부 계좌만 조회
     */
    public List<ExternalAccount> getVerifiedExternalAccounts(Long userId) {
        log.debug("사용자 인증된 외부 계좌 조회: userId={}", userId);
        
        List<ExternalAccount> accounts = externalAccountRepository
                .findByUserIdAndVerificationStatusAndIsActiveTrueOrderByCreatedAtDesc(
                        userId, ExternalAccount.ExternalAccountStatus.VERIFIED);
        
        log.info("인증된 외부 계좌 조회 완료: userId={}, 계좌수={}", userId, accounts.size());
        return accounts;
    }
    
    /**
     * 외부 계좌 등록 및 검증
     */
    @Transactional
    public ExternalAccount registerExternalAccount(Long userId, String accountNumber, 
                                                 String bankCode, String bankName, String accountAlias) {
        log.info("외부 계좌 등록 시작: userId={}, bank={}, accountNumber={}", 
                 userId, bankName, maskAccountNumber(accountNumber));
        
        // 계좌 개수 제한 확인
        long accountCount = externalAccountRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId).size();
        if (accountCount >= MAX_EXTERNAL_ACCOUNTS_PER_USER) {
            throw new IllegalStateException("외부 계좌 개수 한도 초과: 최대 " + MAX_EXTERNAL_ACCOUNTS_PER_USER + "개까지 등록 가능");
        }
        
        // 중복 계좌 확인
        if (externalAccountRepository.existsByUserIdAndAccountNumberAndBankCodeAndIsActiveTrue(
                userId, accountNumber, bankCode)) {
            throw new IllegalStateException("이미 등록된 계좌입니다");
        }
        
        // 외부 계좌 검증
        AccountVerificationRequest verificationRequest = AccountVerificationRequest.builder()
                .accountNumber(accountNumber)
                .bankCode(bankCode)
                .bankName(bankName)
                .userId(userId)
                .verificationLevel("BASIC")
                .build();
        
        AccountVerificationResponse verificationResponse = externalBankApiService.verifyAccount(verificationRequest);
        
        // 외부 계좌 엔티티 생성
        ExternalAccount externalAccount = ExternalAccount.builder()
                .userId(userId)
                .accountNumber(accountNumber)
                .bankCode(bankCode)
                .bankName(bankName)
                .accountAlias(accountAlias)
                .isActive(true)
                .apiProvider("BANK_API") // 실제로는 은행별로 다를 수 있음
                .build();
        
        if (verificationResponse.isSuccess()) {
            externalAccount.setAccountHolderName(verificationResponse.getAccountHolderName());
            externalAccount.markAsVerified();
            log.info("외부 계좌 검증 성공: {}", maskAccountNumber(accountNumber));
        } else {
            externalAccount.markAsVerificationFailed();
            log.warn("외부 계좌 검증 실패: {} - {}", maskAccountNumber(accountNumber), 
                     verificationResponse.getErrorMessage());
        }
        
        ExternalAccount savedAccount = externalAccountRepository.save(externalAccount);
        
        auditLogService.logSuccess("EXTERNAL_ACCOUNT_REGISTERED", "EXTERNAL_ACCOUNT", 
                accountNumber, "외부 계좌 등록", null);
        
        if (!verificationResponse.isSuccess()) {
            throw new RuntimeException("계좌 검증 실패: " + verificationResponse.getErrorMessage());
        }
        
        log.info("외부 계좌 등록 완료: userId={}, accountId={}", userId, savedAccount.getId());
        return savedAccount;
    }
    
    /**
     * 외부 계좌 재검증
     */
    @Transactional
    public ExternalAccount reVerifyExternalAccount(Long userId, Long accountId) {
        log.info("외부 계좌 재검증 시작: userId={}, accountId={}", userId, accountId);
        
        ExternalAccount account = externalAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("외부 계좌를 찾을 수 없습니다"));
        
        if (!account.getUserId().equals(userId)) {
            throw new RuntimeException("접근 권한이 없습니다");
        }
        
        // 외부 API를 통한 재검증
        AccountVerificationRequest verificationRequest = AccountVerificationRequest.builder()
                .accountNumber(account.getAccountNumber())
                .bankCode(account.getBankCode())
                .bankName(account.getBankName())
                .userId(userId)
                .verificationLevel("BASIC")
                .skipCache(true) // 재검증 시 캐시 건너뛰기
                .build();
        
        AccountVerificationResponse verificationResponse = externalBankApiService.verifyAccount(verificationRequest);
        
        if (verificationResponse.isSuccess()) {
            account.markAsVerified();
            account.setAccountHolderName(verificationResponse.getAccountHolderName());
            log.info("외부 계좌 재검증 성공: accountId={}", accountId);
        } else {
            account.markAsVerificationFailed();
            log.warn("외부 계좌 재검증 실패: accountId={} - {}", accountId, 
                     verificationResponse.getErrorMessage());
        }
        
        ExternalAccount updatedAccount = externalAccountRepository.save(account);
        
        auditLogService.logSuccess("EXTERNAL_ACCOUNT_REVERIFIED", "EXTERNAL_ACCOUNT", 
                account.getAccountNumber(), "외부 계좌 재검증", null);
        
        return updatedAccount;
    }
    
    /**
     * 외부 계좌 별칭 변경
     */
    @Transactional
    public ExternalAccount updateAccountAlias(Long userId, Long accountId, String newAlias) {
        log.info("외부 계좌 별칭 변경: userId={}, accountId={}, newAlias={}", userId, accountId, newAlias);
        
        ExternalAccount account = externalAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("외부 계좌를 찾을 수 없습니다"));
        
        if (!account.getUserId().equals(userId)) {
            throw new RuntimeException("접근 권한이 없습니다");
        }
        
        String oldAlias = account.getAccountAlias();
        account.setAccountAlias(newAlias);
        account.setUpdatedAt(LocalDateTime.now());
        
        ExternalAccount updatedAccount = externalAccountRepository.save(account);
        
        log.info("외부 계좌 별칭 변경 완료: {} -> {}", oldAlias, newAlias);
        auditLogService.logSuccess("EXTERNAL_ACCOUNT_ALIAS_UPDATED", "EXTERNAL_ACCOUNT", 
                account.getAccountNumber(), "외부 계좌 별칭 변경", null);
        
        return updatedAccount;
    }
    
    /**
     * 외부 계좌 삭제 (비활성화)
     */
    @Transactional
    public void deleteExternalAccount(Long userId, Long accountId) {
        log.info("외부 계좌 삭제: userId={}, accountId={}", userId, accountId);
        
        ExternalAccount account = externalAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("외부 계좌를 찾을 수 없습니다"));
        
        if (!account.getUserId().equals(userId)) {
            throw new RuntimeException("접근 권한이 없습니다");
        }
        
        account.deactivate();
        account.setUpdatedAt(LocalDateTime.now());
        
        externalAccountRepository.save(account);
        
        log.info("외부 계좌 삭제 완료: accountId={}", accountId);
        auditLogService.logSuccess("EXTERNAL_ACCOUNT_DELETED", "EXTERNAL_ACCOUNT", 
                account.getAccountNumber(), "외부 계좌 삭제", null);
    }
    
    /**
     * 재검증이 필요한 계좌들 조회
     */
    public List<ExternalAccount> getAccountsNeedingReVerification() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        return externalAccountRepository.findAccountsNeedingReVerification(
                ExternalAccount.ExternalAccountStatus.VERIFIED, cutoffDate);
    }
    
    /**
     * 특정 외부 계좌 조회
     */
    public Optional<ExternalAccount> getExternalAccount(Long userId, Long accountId) {
        return externalAccountRepository.findById(accountId)
                .filter(account -> account.getUserId().equals(userId) && account.getIsActive());
    }
    
    /**
     * 계좌번호 마스킹
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        
        int length = accountNumber.length();
        String maskedPart = "*".repeat(Math.max(0, length - 4));
        return accountNumber.substring(0, Math.min(2, length)) + 
               maskedPart + 
               accountNumber.substring(Math.max(2, length - 2));
    }
    
    /**
     * 외부 계좌 통계 정보
     */
    public ExternalAccountStatistics getUserExternalAccountStatistics(Long userId) {
        List<ExternalAccount> allAccounts = getUserExternalAccounts(userId);
        List<ExternalAccount> verifiedAccounts = getVerifiedExternalAccounts(userId);
        
        long pendingCount = allAccounts.stream()
                .filter(account -> account.getVerificationStatus() == ExternalAccount.ExternalAccountStatus.PENDING)
                .count();
        
        long failedCount = allAccounts.stream()
                .filter(account -> account.getVerificationStatus() == ExternalAccount.ExternalAccountStatus.FAILED)
                .count();
        
        return ExternalAccountStatistics.builder()
                .totalAccounts(allAccounts.size())
                .verifiedAccounts(verifiedAccounts.size())
                .pendingAccounts((int) pendingCount)
                .failedAccounts((int) failedCount)
                .build();
    }
    
    /**
     * 외부 계좌 통계 정보 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class ExternalAccountStatistics {
        private int totalAccounts;
        private int verifiedAccounts;
        private int pendingAccounts;
        private int failedAccounts;
    }
}