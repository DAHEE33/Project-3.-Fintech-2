package fintech2.easypay.config;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.account.entity.AccountBalance;
import fintech2.easypay.account.entity.UserAccount;
import fintech2.easypay.account.repository.AccountRepository;
import fintech2.easypay.account.repository.AccountBalanceRepository;
import fintech2.easypay.account.repository.UserAccountRepository;
import fintech2.easypay.auth.entity.User;
import fintech2.easypay.auth.repository.UserRepository;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.entity.TransferStatus;
import fintech2.easypay.transfer.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final UserAccountRepository userAccountRepository;
    private final TransferRepository transferRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("애플리케이션 초기 데이터 생성 시작...");
        
        // 테스트 사용자1이 이미 존재하는지 확인 (하이픈 제거된 형태로 검색)
        if (userRepository.findByPhoneNumber("01012345678").isEmpty()) {
            createTestUser1();
        } else {
            log.info("테스트 사용자1이 이미 존재합니다.");
        }
        
        // 테스트 사용자2가 이미 존재하는지 확인 (하이픈 제거된 형태로 검색)
        if (userRepository.findByPhoneNumber("01098765432").isEmpty()) {
            createTestUser2();
        } else {
            log.info("테스트 사용자2가 이미 존재합니다.");
        }
        
        // 임시 테스트 사용자 추가 (PIN 미설정)
        if (userRepository.findByPhoneNumber("01012341234").isEmpty()) {
            createTempTestUser();
        } else {
            log.info("임시 테스트 사용자가 이미 존재합니다.");
        }
        
        // 테스트 송금 데이터 생성 (메모 표시 테스트용)
        createTestTransfers();
        
        log.info("애플리케이션 초기 데이터 생성 완료!");
    }

    private void createTestUser1() {
        try {
            log.info("테스트 사용자1 생성 중...");
            
            // 사용자 생성 (하이픈 제거된 전화번호로 저장)
            User user1 = User.builder()
                    .phoneNumber("01012345678") // 하이픈 제거
                    .email("test1@example.com")
                    .password(passwordEncoder.encode("123456"))
                    .name("테스트사용자1")
                    .createdAt(LocalDateTime.now())
                    .transferPin(passwordEncoder.encode("123456")) // 테스트용 기본 PIN
                    .pinCreatedAt(LocalDateTime.now())
                    .build();
            user1 = userRepository.save(user1);
            
            // 첫 번째 계좌 생성 (주계좌)
            String accountNumber1 = "EP" + String.format("%010d", user1.getId());
            
            Account account1 = Account.builder()
                    .accountNumber(accountNumber1)
                    .userId(user1.getId())
                    .balance(new BigDecimal("1000000")) // 초기 잔액 100만원
                    .createdAt(LocalDateTime.now())
                    .build();
            accountRepository.save(account1);
            
            AccountBalance accountBalance1 = AccountBalance.builder()
                    .accountNumber(accountNumber1)
                    .balance(new BigDecimal("1000000"))
                    .build();
            accountBalanceRepository.save(accountBalance1);
            
            // 두 번째 계좌 생성 (예적금 계좌)
            String accountNumber2 = "EP" + String.format("%010d", user1.getId() + 1000);
            
            Account account2 = Account.builder()
                    .accountNumber(accountNumber2)
                    .userId(user1.getId())
                    .balance(new BigDecimal("500000")) // 초기 잔액 50만원
                    .createdAt(LocalDateTime.now())
                    .build();
            accountRepository.save(account2);
            
            AccountBalance accountBalance2 = AccountBalance.builder()
                    .accountNumber(accountNumber2)
                    .balance(new BigDecimal("500000"))
                    .build();
            accountBalanceRepository.save(accountBalance2);
            
            // User 엔티티에 주계좌번호 설정 (첫 번째 계좌를 주계좌로)
            user1.setAccountNumber(accountNumber1);
            userRepository.save(user1);
            
            // UserAccount 데이터 생성 (다중 계좌 시스템용)
            UserAccount userAccount1 = UserAccount.builder()
                    .userId(user1.getId())
                    .accountNumber(accountNumber1)
                    .accountName("주거래계좌")
                    .balance(new BigDecimal("1000000")) // 잔액 설정
                    .isPrimary(true)
                    .build();
            userAccountRepository.save(userAccount1);
            
            UserAccount userAccount2 = UserAccount.builder()
                    .userId(user1.getId())
                    .accountNumber(accountNumber2)
                    .accountName("예적금계좌")
                    .balance(new BigDecimal("500000")) // 잔액 설정
                    .isPrimary(false)
                    .build();
            userAccountRepository.save(userAccount2);
            
            log.info("테스트 사용자1 생성 완료: 전화번호={}, 주계좌={} (1,000,000원), 예적금계좌={} (500,000원)", 
                    user1.getPhoneNumber(), accountNumber1, accountNumber2);
                    
        } catch (Exception e) {
            log.error("테스트 사용자1 생성 실패: {}", e.getMessage(), e);
        }
    }

    private void createTestUser2() {
        try {
            log.info("테스트 사용자2 생성 중...");
            
            // 사용자 생성 (하이픈 제거된 전화번호로 저장)
            User user2 = User.builder()
                    .phoneNumber("01098765432") // 하이픈 제거
                    .email("test2@example.com")
                    .password(passwordEncoder.encode("123456"))
                    .name("테스트사용자2")
                    .createdAt(LocalDateTime.now())
                    .transferPin(passwordEncoder.encode("123456")) // 테스트용 기본 PIN
                    .pinCreatedAt(LocalDateTime.now())
                    .build();
            user2 = userRepository.save(user2);
            
            // 첫 번째 계좌 생성 (주계좌)
            String accountNumber1 = "EP" + String.format("%010d", user2.getId());
            
            Account account1 = Account.builder()
                    .accountNumber(accountNumber1)
                    .userId(user2.getId())
                    .balance(new BigDecimal("750000")) // 초기 잔액 75만원
                    .createdAt(LocalDateTime.now())
                    .build();
            accountRepository.save(account1);
            
            AccountBalance accountBalance1 = AccountBalance.builder()
                    .accountNumber(accountNumber1)
                    .balance(new BigDecimal("750000"))
                    .build();
            accountBalanceRepository.save(accountBalance1);
            
            // 두 번째 계좌 생성 (예적금 계좌)
            String accountNumber2 = "EP" + String.format("%010d", user2.getId() + 1000);
            
            Account account2 = Account.builder()
                    .accountNumber(accountNumber2)
                    .userId(user2.getId())
                    .balance(new BigDecimal("300000")) // 초기 잔액 30만원
                    .createdAt(LocalDateTime.now())
                    .build();
            accountRepository.save(account2);
            
            AccountBalance accountBalance2 = AccountBalance.builder()
                    .accountNumber(accountNumber2)
                    .balance(new BigDecimal("300000"))
                    .build();
            accountBalanceRepository.save(accountBalance2);
            
            // User 엔티티에 주계좌번호 설정 (첫 번째 계좌를 주계좌로)
            user2.setAccountNumber(accountNumber1);
            userRepository.save(user2);
            
            // UserAccount 데이터 생성 (다중 계좌 시스템용)
            UserAccount userAccount1 = UserAccount.builder()
                    .userId(user2.getId())
                    .accountNumber(accountNumber1)
                    .accountName("주거래계좌")
                    .balance(new BigDecimal("750000")) // 잔액 설정
                    .isPrimary(true)
                    .build();
            userAccountRepository.save(userAccount1);
            
            UserAccount userAccount2 = UserAccount.builder()
                    .userId(user2.getId())
                    .accountNumber(accountNumber2)
                    .accountName("예적금계좌")
                    .balance(new BigDecimal("300000")) // 잔액 설정
                    .isPrimary(false)
                    .build();
            userAccountRepository.save(userAccount2);
            
            log.info("테스트 사용자2 생성 완료: 전화번호={}, 주계좌={} (750,000원), 예적금계좌={} (300,000원)", 
                    user2.getPhoneNumber(), accountNumber1, accountNumber2);
                    
        } catch (Exception e) {
            log.error("테스트 사용자2 생성 실패: {}", e.getMessage(), e);
        }
    }
    
    private void createTempTestUser() {
        try {
            log.info("임시 테스트 사용자 생성 중...");
            
            // 사용자 생성 (하이픈 제거된 전화번호로 저장, PIN 미설정)
            User tempUser = User.builder()
                    .phoneNumber("01012341234") // 하이픈 제거
                    .email("temp@example.com")
                    .password(passwordEncoder.encode("test1234"))
                    .name("임시테스트사용자")
                    .createdAt(LocalDateTime.now())
                    .transferPin(null) // PIN 미설정
                    .pinCreatedAt(null) // PIN 생성일 미설정
                    .build();
            tempUser = userRepository.save(tempUser);
            
            // 계좌번호 생성 (EasyPay 가상계좌)
            String accountNumber = "EP" + String.format("%010d", tempUser.getId());
            
            // Account 엔티티 생성
            Account account = Account.builder()
                    .accountNumber(accountNumber)
                    .userId(tempUser.getId())
                    .balance(new BigDecimal("100000")) // 초기 잔액 10만원
                    .createdAt(LocalDateTime.now())
                    .build();
            accountRepository.save(account);
            
            // AccountBalance 엔티티 생성 (BalanceService에서 사용)
            AccountBalance accountBalance = AccountBalance.builder()
                    .accountNumber(accountNumber)
                    .balance(new BigDecimal("100000"))
                    .build();
            accountBalanceRepository.save(accountBalance);
            
            // User 엔티티에 계좌번호 설정
            tempUser.setAccountNumber(accountNumber);
            userRepository.save(tempUser);
            
            log.info("임시 테스트 사용자 생성 완료: 전화번호={}, 계좌번호={}, 초기잔액=100,000원, PIN=미설정", 
                    tempUser.getPhoneNumber(), accountNumber);
                    
        } catch (Exception e) {
            log.error("임시 테스트 사용자 생성 실패: {}", e.getMessage(), e);
        }
    }

    private void createTestTransfers() {
        try {
            log.info("테스트 송금 데이터 생성 중...");
            
            // 이미 송금 데이터가 있는지 확인
            if (transferRepository.count() > 0) {
                log.info("송금 데이터가 이미 존재합니다.");
                return;
            }
            
            // 사용자1과 사용자2 조회
            User user1 = userRepository.findByPhoneNumber("01012345678").orElse(null);
            User user2 = userRepository.findByPhoneNumber("01098765432").orElse(null);
            User tempUser = userRepository.findByPhoneNumber("01012341234").orElse(null);
            
            if (user1 == null || user2 == null || tempUser == null) {
                log.warn("사용자가 존재하지 않아 테스트 송금 데이터를 생성할 수 없습니다.");
                return;
            }
            
            // 계좌 정보 가져오기
            String user1Account = "EP" + String.format("%010d", user1.getId());
            String user2Account = "EP" + String.format("%010d", user2.getId());
            String tempUserAccount = "EP" + String.format("%010d", tempUser.getId());
            
            // 테스트 송금 1: 사용자1 -> 사용자2 (메모 있음)
            Transfer transfer1 = Transfer.builder()
                    .transferId(generateTransferId())
                    .transactionId(generateTransactionId())
                    .sender(user1)
                    .senderAccountNumber(user1Account)
                    .receiver(user2)
                    .receiverAccountNumber(user2Account)
                    .amount(new BigDecimal("50000"))
                    .description("점심값 보내드립니다! 맛있게 드세요 😊") // memo -> description
                    .status(TransferStatus.COMPLETED)
                    .processedAt(LocalDateTime.now().minusHours(2))
                    .build();
            transferRepository.save(transfer1);
            
            // 테스트 송금 2: 사용자1 -> 임시사용자 (메모 있음)
            Transfer transfer2 = Transfer.builder()
                    .transferId(generateTransferId())
                    .transactionId(generateTransactionId())
                    .sender(user1)
                    .senderAccountNumber(user1Account)
                    .receiver(tempUser)
                    .receiverAccountNumber(tempUserAccount)
                    .amount(new BigDecimal("30000"))
                    .description("생일축하해! 생일선물이야 🎉") // memo -> description
                    .status(TransferStatus.COMPLETED)
                    .processedAt(LocalDateTime.now().minusHours(1))
                    .build();
            transferRepository.save(transfer2);
            
            // 테스트 송금 3: 사용자1 -> 사용자2 (메모 없음)
            Transfer transfer3 = Transfer.builder()
                    .transferId(generateTransferId())
                    .transactionId(generateTransactionId())
                    .sender(user1)
                    .senderAccountNumber(user1Account)
                    .receiver(user2)
                    .receiverAccountNumber(user2Account)
                    .amount(new BigDecimal("100000"))
                    .description(null) // 메모 없음
                    .status(TransferStatus.COMPLETED)
                    .processedAt(LocalDateTime.now().minusMinutes(30))
                    .build();
            transferRepository.save(transfer3);
            
            // 테스트 송금 4: 사용자1 -> 임시사용자 (긴 메모)
            Transfer transfer4 = Transfer.builder()
                    .transferId(generateTransferId())
                    .transactionId(generateTransactionId())
                    .sender(user1)
                    .senderAccountNumber(user1Account)
                    .receiver(tempUser)
                    .receiverAccountNumber(tempUserAccount)
                    .amount(new BigDecimal("25000"))
                    .description("프로젝트 회식비용입니다. 다들 고생 많으셨어요! 오늘 저녁에는 맛있는 걸 드세요.") // memo -> description
                    .status(TransferStatus.COMPLETED)
                    .processedAt(LocalDateTime.now().minusMinutes(10))
                    .build();
            transferRepository.save(transfer4);
            
            log.info("테스트 송금 데이터 생성 완료: 총 {}건", 4);
            
        } catch (Exception e) {
            log.error("테스트 송금 데이터 생성 실패: {}", e.getMessage(), e);
        }
    }
    
    private String generateTransactionId() {
        return "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
    
    private String generateTransferId() {
        return "TRF" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}