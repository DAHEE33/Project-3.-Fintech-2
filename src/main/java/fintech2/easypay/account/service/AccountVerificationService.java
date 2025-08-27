package fintech2.easypay.account.service;

import fintech2.easypay.account.dto.AccountVerificationRequest;
import fintech2.easypay.account.dto.AccountVerificationResponse;
import fintech2.easypay.account.repository.AccountRepository;
import fintech2.easypay.account.entity.Account;
import fintech2.easypay.auth.entity.User;
import fintech2.easypay.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountVerificationService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    // 임시 외부 은행 계좌 데이터 (테스트용)
    private static final Map<String, Map<String, String>> TEMP_EXTERNAL_ACCOUNTS = new HashMap<>();
    
    static {
        // 카카오뱅크 임시 계좌
        Map<String, String> kakaoAccounts = new HashMap<>();
        kakaoAccounts.put("3333-01-1234567", "김카카오");
        kakaoAccounts.put("3333-01-7654321", "박카카오");
        TEMP_EXTERNAL_ACCOUNTS.put("카카오뱅크", kakaoAccounts);
        
        // 토스뱅크 임시 계좌
        Map<String, String> tossAccounts = new HashMap<>();
        tossAccounts.put("100-2345-678901", "이토스");
        tossAccounts.put("100-2345-109876", "최토스");
        TEMP_EXTERNAL_ACCOUNTS.put("토스뱅크", tossAccounts);
        
        // 국민은행 임시 계좌
        Map<String, String> kbAccounts = new HashMap<>();
        kbAccounts.put("123456-04-123456", "정국민");
        kbAccounts.put("654321-04-654321", "한국민");
        TEMP_EXTERNAL_ACCOUNTS.put("국민은행", kbAccounts);
        
        // 신한은행 임시 계좌
        Map<String, String> shinhanAccounts = new HashMap<>();
        shinhanAccounts.put("110-123-456789", "송신한");
        shinhanAccounts.put("110-987-654321", "윤신한");
        TEMP_EXTERNAL_ACCOUNTS.put("신한은행", shinhanAccounts);
    }

    public AccountVerificationResponse verifyAccount(AccountVerificationRequest request) {
        String accountNumber = request.getAccountNumber().trim();
        String bankName = request.getBankName().trim();
        
        log.info("계좌 검증 요청: 계좌번호={}, 은행={}", accountNumber, bankName);
        
        // EasyPay 은행인 경우 DB에서 조회
        if ("EasyPay".equals(bankName)) {
            return verifyEasyPayAccount(accountNumber);
        }
        
        // 외부 은행인 경우 임시 데이터로 검증 (향후 실제 API 연동 예정)
        return verifyExternalBankAccount(accountNumber, bankName);
    }
    
    private AccountVerificationResponse verifyEasyPayAccount(String accountNumber) {
        log.info("EasyPay 계좌 검증: {}", accountNumber);
        
        Optional<Account> accountOpt = accountRepository.findByAccountNumber(accountNumber);
        if (accountOpt.isEmpty()) {
            return AccountVerificationResponse.failure("존재하지 않는 계좌번호입니다.");
        }
        
        Account account = accountOpt.get();
        Optional<User> userOpt = userRepository.findById(account.getUserId());
        if (userOpt.isEmpty()) {
            return AccountVerificationResponse.failure("계좌 소유자 정보를 찾을 수 없습니다.");
        }
        
        User user = userOpt.get();
        log.info("EasyPay 계좌 검증 성공: 계좌소유자={}", user.getName());
        
        return AccountVerificationResponse.success(user.getName(), "EasyPay");
    }
    
    private AccountVerificationResponse verifyExternalBankAccount(String accountNumber, String bankName) {
        log.info("외부 은행 계좌 검증: 은행={}, 계좌번호={}", bankName, accountNumber);
        
        Map<String, String> bankAccounts = TEMP_EXTERNAL_ACCOUNTS.get(bankName);
        if (bankAccounts == null) {
            return AccountVerificationResponse.failure("지원하지 않는 은행입니다.");
        }
        
        String accountHolderName = bankAccounts.get(accountNumber);
        if (accountHolderName == null) {
            return AccountVerificationResponse.failure("존재하지 않는 계좌번호입니다.");
        }
        
        log.info("외부 은행 계좌 검증 성공: 은행={}, 계좌소유자={}", bankName, accountHolderName);
        
        return AccountVerificationResponse.success(accountHolderName, bankName);
    }
}