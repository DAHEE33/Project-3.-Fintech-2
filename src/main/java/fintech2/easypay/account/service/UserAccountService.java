package fintech2.easypay.account.service;

import fintech2.easypay.account.entity.UserAccount;
import fintech2.easypay.account.repository.UserAccountRepository;
import fintech2.easypay.account.entity.TransactionHistory;
import fintech2.easypay.account.repository.TransactionHistoryRepository;
import fintech2.easypay.common.enums.AccountStatus;
import fintech2.easypay.common.enums.TransactionStatus;
import fintech2.easypay.common.enums.TransactionType;
import fintech2.easypay.common.exception.AccountNotFoundException;
import fintech2.easypay.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 다중 계좌 관리 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccountService {
    
    private final UserAccountRepository userAccountRepository;
    private final TransactionHistoryRepository transactionHistoryRepository;
    private final AuditLogService auditLogService;
    
    private static final int MAX_ACCOUNTS_PER_USER = 5; // 사용자당 최대 계좌 수
    private static final String ACCOUNT_PREFIX = "EP"; // EasyPay 계좌 접두어
    
    /**
     * 사용자의 모든 활성 계좌 조회
     */
    public List<UserAccount> getUserAccounts(Long userId) {
        log.debug("사용자 계좌 목록 조회 시작: userId={}", userId);
        
        List<UserAccount> accounts = userAccountRepository
                .findByUserIdAndStatusOrderByIsPrimaryDescCreatedAtAsc(userId, AccountStatus.ACTIVE);
        
        log.info("사용자 계좌 목록 조회 완료: userId={}, 계좌수={}", userId, accounts.size());
        auditLogService.logSuccess("USER_ACCOUNTS_INQUIRY", "ACCOUNT", String.valueOf(userId), 
                "사용자 계좌 목록 조회", null);
        
        return accounts;
    }
    
    /**
     * 사용자의 기본 계좌 조회
     */
    public Optional<UserAccount> getPrimaryAccount(Long userId) {
        log.debug("기본 계좌 조회: userId={}", userId);
        
        Optional<UserAccount> primaryAccount = userAccountRepository.findByUserIdAndIsPrimaryTrue(userId);
        
        if (primaryAccount.isPresent()) {
            log.info("기본 계좌 조회 성공: userId={}, accountNumber={}", 
                     userId, primaryAccount.get().getAccountNumber());
        } else {
            log.warn("기본 계좌 없음: userId={}", userId);
        }
        
        return primaryAccount;
    }
    
    /**
     * 새로운 EasyPay 계좌 생성
     */
    @Transactional
    public UserAccount createNewAccount(Long userId, String accountName, String userPin) {
        log.info("새 계좌 생성 시작: userId={}, accountName={}", userId, accountName);
        
        // 계좌 개수 제한 확인
        long accountCount = userAccountRepository.countByUserIdAndStatus(userId, AccountStatus.ACTIVE);
        if (accountCount >= MAX_ACCOUNTS_PER_USER) {
            throw new IllegalStateException("계좌 개수 한도 초과: 최대 " + MAX_ACCOUNTS_PER_USER + "개까지 생성 가능");
        }
        
        // 고유한 계좌번호 생성
        String newAccountNumber = generateUniqueAccountNumber();
        
        // 첫 번째 계좌인 경우 기본 계좌로 설정
        boolean isPrimary = accountCount == 0;
        
        UserAccount newAccount = UserAccount.builder()
                .userId(userId)
                .accountNumber(newAccountNumber)
                .accountName(accountName)
                .balance(BigDecimal.ZERO)
                .isPrimary(isPrimary)
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        
        UserAccount savedAccount = userAccountRepository.save(newAccount);
        
        log.info("새 계좌 생성 완료: userId={}, accountNumber={}, isPrimary={}", 
                 userId, newAccountNumber, isPrimary);
        
        auditLogService.logSuccess("NEW_ACCOUNT_CREATED", "ACCOUNT", newAccountNumber, 
                "새 계좌 생성", null);
        
        return savedAccount;
    }
    
    /**
     * 계좌 별칭 변경
     */
    @Transactional
    public UserAccount updateAccountName(Long userId, String accountNumber, String newAccountName) {
        log.info("계좌 별칭 변경: userId={}, accountNumber={}, newName={}", 
                 userId, accountNumber, newAccountName);
        
        UserAccount account = userAccountRepository.findByUserIdAndAccountNumber(userId, accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("계좌를 찾을 수 없습니다: " + accountNumber));
        
        String oldName = account.getAccountName();
        account.setAccountName(newAccountName);
        account.setUpdatedAt(LocalDateTime.now());
        
        UserAccount updatedAccount = userAccountRepository.save(account);
        
        log.info("계좌 별칭 변경 완료: {} -> {}", oldName, newAccountName);
        auditLogService.logSuccess("ACCOUNT_NAME_UPDATED", "ACCOUNT", accountNumber, 
                "계좌 별칭 변경", null);
        
        return updatedAccount;
    }
    
    /**
     * 기본 계좌 변경
     */
    @Transactional
    public void changePrimaryAccount(Long userId, String newPrimaryAccountNumber) {
        log.info("기본 계좌 변경: userId={}, newPrimaryAccount={}", userId, newPrimaryAccountNumber);
        
        // 현재 기본 계좌 해제
        userAccountRepository.findByUserIdAndIsPrimaryTrue(userId)
                .ifPresent(currentPrimary -> {
                    currentPrimary.unsetPrimary();
                    userAccountRepository.save(currentPrimary);
                    log.debug("기존 기본 계좌 해제: {}", currentPrimary.getAccountNumber());
                });
        
        // 새로운 기본 계좌 설정
        UserAccount newPrimaryAccount = userAccountRepository
                .findByUserIdAndAccountNumber(userId, newPrimaryAccountNumber)
                .orElseThrow(() -> new AccountNotFoundException("계좌를 찾을 수 없습니다: " + newPrimaryAccountNumber));
        
        newPrimaryAccount.setPrimary();
        newPrimaryAccount.setUpdatedAt(LocalDateTime.now());
        userAccountRepository.save(newPrimaryAccount);
        
        log.info("기본 계좌 변경 완료: {}", newPrimaryAccountNumber);
        auditLogService.logSuccess("PRIMARY_ACCOUNT_CHANGED", "ACCOUNT", newPrimaryAccountNumber, 
                "기본 계좌 변경", null);
    }
    
    /**
     * 계좌 비활성화 (삭제 대신)
     */
    @Transactional
    public void deactivateAccount(Long userId, String accountNumber) {
        log.info("계좌 비활성화: userId={}, accountNumber={}", userId, accountNumber);
        
        UserAccount account = userAccountRepository.findByUserIdAndAccountNumber(userId, accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("계좌를 찾을 수 없습니다: " + accountNumber));
        
        // 기본 계좌는 비활성화 불가
        if (account.getIsPrimary()) {
            throw new IllegalStateException("기본 계좌는 비활성화할 수 없습니다");
        }
        
        // 잔액이 있는 계좌는 비활성화 불가
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("잔액이 있는 계좌는 비활성화할 수 없습니다");
        }
        
        account.deactivate();
        account.setUpdatedAt(LocalDateTime.now());
        userAccountRepository.save(account);
        
        log.info("계좌 비활성화 완료: {}", accountNumber);
        auditLogService.logSuccess("ACCOUNT_DEACTIVATED", "ACCOUNT", accountNumber, 
                "계좌 비활성화", null);
    }
    
    /**
     * 계좌번호로 계좌 조회
     */
    public Optional<UserAccount> getAccountByNumber(String accountNumber) {
        return userAccountRepository.findByAccountNumber(accountNumber);
    }
    
    /**
     * 기본 계좌 입금
     */
    @Transactional
    public UserAccount depositToPrimaryAccount(Long userId, BigDecimal amount, String memo) {
        log.info("기본 계좌 입금: userId={}, amount={}", userId, amount);
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("입금 금액은 0보다 커야 합니다");
        }
        
        UserAccount primaryAccount = getPrimaryAccount(userId)
                .orElseThrow(() -> new AccountNotFoundException("기본 계좌를 찾을 수 없습니다"));
        
        BigDecimal oldBalance = primaryAccount.getBalance();
        BigDecimal newBalance = oldBalance.add(amount);
        
        primaryAccount.setBalance(newBalance);
        primaryAccount.setUpdatedAt(LocalDateTime.now());
        
        UserAccount updatedAccount = userAccountRepository.save(primaryAccount);
        
        // 거래 내역 저장
        createTransactionHistory(
                updatedAccount.getAccountNumber(), 
                amount, 
                TransactionType.DEPOSIT, 
                memo != null ? memo : "입금", 
                userId.toString(),
                newBalance
        );
        
        log.info("기본 계좌 입금 완료: accountNumber={}, {} -> {}", 
                 primaryAccount.getAccountNumber(), oldBalance, newBalance);
        
        auditLogService.logSuccess("DEPOSIT", "ACCOUNT", primaryAccount.getAccountNumber(), 
                memo != null ? memo : "입금", null);
        
        return updatedAccount;
    }
    
    /**
     * 기본 계좌 출금
     */
    @Transactional
    public UserAccount withdrawFromPrimaryAccount(Long userId, BigDecimal amount, String memo) {
        log.info("기본 계좌 출금: userId={}, amount={}", userId, amount);
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("출금 금액은 0보다 커야 합니다");
        }
        
        UserAccount primaryAccount = getPrimaryAccount(userId)
                .orElseThrow(() -> new AccountNotFoundException("기본 계좌를 찾을 수 없습니다"));
        
        BigDecimal oldBalance = primaryAccount.getBalance();
        
        if (oldBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("잔액이 부족합니다");
        }
        
        BigDecimal newBalance = oldBalance.subtract(amount);
        
        primaryAccount.setBalance(newBalance);
        primaryAccount.setUpdatedAt(LocalDateTime.now());
        
        UserAccount updatedAccount = userAccountRepository.save(primaryAccount);
        
        // 거래 내역 저장
        createTransactionHistory(
                updatedAccount.getAccountNumber(), 
                amount, 
                TransactionType.WITHDRAWAL, 
                memo != null ? memo : "출금", 
                userId.toString(),
                newBalance
        );
        
        log.info("기본 계좌 출금 완료: accountNumber={}, {} -> {}", 
                 primaryAccount.getAccountNumber(), oldBalance, newBalance);
        
        auditLogService.logSuccess("WITHDRAW", "ACCOUNT", primaryAccount.getAccountNumber(), 
                memo != null ? memo : "출금", null);
        
        return updatedAccount;
    }
    
    /**
     * 특정 계좌 입금
     */
    @Transactional
    public UserAccount depositToAccount(Long userId, String accountNumber, BigDecimal amount, String memo) {
        log.info("계좌 입금: userId={}, accountNumber={}, amount={}", userId, accountNumber, amount);
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("입금 금액은 0보다 커야 합니다");
        }
        
        UserAccount account = userAccountRepository.findByUserIdAndAccountNumber(userId, accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("계좌를 찾을 수 없습니다: " + accountNumber));
        
        BigDecimal oldBalance = account.getBalance();
        BigDecimal newBalance = oldBalance.add(amount);
        
        account.setBalance(newBalance);
        account.setUpdatedAt(LocalDateTime.now());
        
        UserAccount updatedAccount = userAccountRepository.save(account);
        
        log.info("계좌 입금 완료: accountNumber={}, {} -> {}", 
                 accountNumber, oldBalance, newBalance);
        
        auditLogService.logSuccess("DEPOSIT", "ACCOUNT", accountNumber, 
                memo != null ? memo : "입금", null);
        
        return updatedAccount;
    }
    
    /**
     * 특정 계좌 출금
     */
    @Transactional
    public UserAccount withdrawFromAccount(Long userId, String accountNumber, BigDecimal amount, String memo) {
        log.info("계좌 출금: userId={}, accountNumber={}, amount={}", userId, accountNumber, amount);
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("출금 금액은 0보다 커야 합니다");
        }
        
        UserAccount account = userAccountRepository.findByUserIdAndAccountNumber(userId, accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("계좌를 찾을 수 없습니다: " + accountNumber));
        
        BigDecimal oldBalance = account.getBalance();
        
        if (oldBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("잔액이 부족합니다");
        }
        
        BigDecimal newBalance = oldBalance.subtract(amount);
        
        account.setBalance(newBalance);
        account.setUpdatedAt(LocalDateTime.now());
        
        UserAccount updatedAccount = userAccountRepository.save(account);
        
        log.info("계좌 출금 완료: accountNumber={}, {} -> {}", 
                 accountNumber, oldBalance, newBalance);
        
        auditLogService.logSuccess("WITHDRAW", "ACCOUNT", accountNumber, 
                memo != null ? memo : "출금", null);
        
        return updatedAccount;
    }
    
    /**
     * 고유한 계좌번호 생성
     */
    private String generateUniqueAccountNumber() {
        String accountNumber;
        int attempts = 0;
        int maxAttempts = 10;
        
        do {
            accountNumber = generateAccountNumber();
            attempts++;
            
            if (attempts > maxAttempts) {
                throw new RuntimeException("계좌번호 생성 실패: 최대 시도 횟수 초과");
            }
        } while (userAccountRepository.existsByAccountNumber(accountNumber));
        
        log.debug("계좌번호 생성 완료: {} (시도 횟수: {})", accountNumber, attempts);
        return accountNumber;
    }
    
    /**
     * 계좌번호 생성 (EP + 10자리 숫자)
     */
    private String generateAccountNumber() {
        // EP + 현재 시간(밀리초) 뒤 7자리 + 랜덤 3자리
        long timestamp = System.currentTimeMillis();
        String timestampStr = String.valueOf(timestamp);
        String lastSevenDigits = timestampStr.substring(Math.max(0, timestampStr.length() - 7));
        
        String randomPart = String.valueOf((int)(Math.random() * 1000)).formatted("%03d");
        
        return ACCOUNT_PREFIX + lastSevenDigits + randomPart;
    }
    
    /**
     * 사용자 계좌 통계 조회
     */
    public AccountStatistics getUserAccountStatistics(Long userId) {
        List<UserAccount> accounts = getUserAccounts(userId);
        
        BigDecimal totalBalance = accounts.stream()
                .map(UserAccount::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return AccountStatistics.builder()
                .totalAccounts(accounts.size())
                .activeAccounts((int) accounts.stream().filter(UserAccount::isActive).count())
                .totalBalance(totalBalance)
                .primaryAccountNumber(accounts.stream()
                        .filter(UserAccount::getIsPrimary)
                        .findFirst()
                        .map(UserAccount::getAccountNumber)
                        .orElse(null))
                .build();
    }
    
    /**
     * 거래 내역 생성
     */
    private void createTransactionHistory(String accountNumber, BigDecimal amount, 
                                        TransactionType transactionType, String description, 
                                        String userId, BigDecimal balanceAfter) {
        try {
            TransactionHistory transaction = TransactionHistory.builder()
                    .accountNumber(accountNumber)
                    .amount(amount)
                    .transactionType(transactionType)
                    .description(description)
                    .balanceAfter(balanceAfter)
                    .status(TransactionStatus.COMPLETED)
                    .transactionId(generateTransactionId())
                    .createdBy(userId)
                    .build();
            
            transactionHistoryRepository.save(transaction);
            log.debug("거래 내역 저장 완료: accountNumber={}, type={}, amount={}", 
                     accountNumber, transactionType, amount);
        } catch (Exception e) {
            log.error("거래 내역 저장 실패: accountNumber={}, type={}, amount={}", 
                     accountNumber, transactionType, amount, e);
            // 거래 내역 저장 실패가 메인 로직에 영향을 주지 않도록 예외를 삼킨다
        }
    }
    
    /**
     * 거래 ID 생성
     */
    private String generateTransactionId() {
        return "USR" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
    
    /**
     * 계좌 통계 정보 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class AccountStatistics {
        private int totalAccounts;
        private int activeAccounts;
        private BigDecimal totalBalance;
        private String primaryAccountNumber;
    }
}