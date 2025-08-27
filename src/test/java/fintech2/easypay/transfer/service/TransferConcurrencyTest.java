package fintech2.easypay.transfer.service;

import fintech2.easypay.account.service.BalanceService;
import fintech2.easypay.auth.entity.User;
import fintech2.easypay.auth.repository.UserRepository;
import fintech2.easypay.common.BusinessException;
import fintech2.easypay.common.enums.TransactionType;
import fintech2.easypay.transfer.dto.TransferRequest;
import fintech2.easypay.transfer.dto.TransferResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 송금 동시성 제어 테스트
 * 송금에서 락 처리가 잘 되어있는지 확인하는 테스트들
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class TransferConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private UserRepository userRepository;

    private static final String SENDER_PHONE = "010-1111-1111";
    private static final String RECEIVER_PHONE = "010-2222-2222";
    private static final String SENDER_ACCOUNT = "VA1111111111";
    private static final String RECEIVER_ACCOUNT = "VA2222222222";

    @BeforeEach
    void setUp() {
        // 테스트용 사용자 및 계좌 설정
        setupTestUsers();
    }

    private void setupTestUsers() {
        // 송금자 설정
        User sender = User.builder()
                .phoneNumber(SENDER_PHONE)
                .email("sender@test.com")
                .password("password")
                .name("송금자")
                .accountNumber(SENDER_ACCOUNT)
                .build();
        userRepository.save(sender);

        // 수신자 설정
        User receiver = User.builder()
                .phoneNumber(RECEIVER_PHONE)
                .email("receiver@test.com")
                .password("password")
                .name("수신자")
                .accountNumber(RECEIVER_ACCOUNT)
                .build();
        userRepository.save(receiver);

        // 송금자 잔액 설정 (100만원)
        balanceService.increase(SENDER_ACCOUNT, new BigDecimal("1000000"), 
            TransactionType.DEPOSIT, "테스트 설정", null, sender.getId().toString());
    }

    @Test
    @DisplayName("동시 송금 시 데드락 방지 확인")
    void concurrentTransfer_shouldNotDeadlock() throws InterruptedException {
        // Given: 송금자 잔액 100만원, 수신자 잔액 0원

        // When: 두 개의 동시 송금 요청
        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 송금 1: 30만원
        executor.submit(() -> {
            try {
                TransferRequest request1 = new TransferRequest();
                request1.setReceiverAccountNumber(RECEIVER_ACCOUNT);
                request1.setAmount(new BigDecimal("300000"));
                request1.setMemo("동시 송금 테스트 1");

                TransferResponse response1 = transferService.transfer(SENDER_PHONE, request1);
                if ("COMPLETED".equals(response1.getStatus())) {
                    successCount.incrementAndGet();
                    log.info("송금 1 성공: 30만원");
                }
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.warn("송금 1 실패: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // 송금 2: 40만원
        executor.submit(() -> {
            try {
                TransferRequest request2 = new TransferRequest();
                request2.setReceiverAccountNumber(RECEIVER_ACCOUNT);
                request2.setAmount(new BigDecimal("400000"));
                request2.setMemo("동시 송금 테스트 2");

                TransferResponse response2 = transferService.transfer(SENDER_PHONE, request2);
                if ("COMPLETED".equals(response2.getStatus())) {
                    successCount.incrementAndGet();
                    log.info("송금 2 성공: 40만원");
                }
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.warn("송금 2 실패: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // Then: 타임아웃 없이 정상 완료되어야 함
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("동시 송금 테스트 결과 - 성공: {}, 실패: {}, 완료여부: {}", 
                successCount.get(), failureCount.get(), completed);

        // 검증: 데드락이 발생하지 않았는지 (타임아웃 없이 완료)
        assertThat(completed).isTrue();
        
        // 검증: 최소 하나는 성공했는지
        assertThat(successCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("잔액 부족 시 송금 실패 확인")
    void insufficientBalance_shouldFailTransfer() {
        // Given: 송금자 잔액 10만원
        balanceService.decrease(SENDER_ACCOUNT, new BigDecimal("900000"), 
            TransactionType.TRANSFER_OUT, "잔액 조정", null, "user1");

        // When & Then: 20만원 송금 시도 시 실패
        TransferRequest request = new TransferRequest();
        request.setReceiverAccountNumber(RECEIVER_ACCOUNT);
        request.setAmount(new BigDecimal("200000"));
        request.setMemo("잔액 부족 테스트");

        assertThatThrownBy(() -> {
            transferService.transfer(SENDER_PHONE, request);
        }).isInstanceOf(BusinessException.class)
          .hasMessageContaining("INSUFFICIENT_BALANCE");
    }

    @Test
    @DisplayName("동일 계좌 간 송금 방지 확인")
    void sameAccountTransfer_shouldBePrevented() {
        // When & Then: 송금자와 수신자가 같은 경우 실패
        TransferRequest request = new TransferRequest();
        request.setReceiverAccountNumber(SENDER_ACCOUNT); // 송금자 계좌로 송금
        request.setAmount(new BigDecimal("100000"));
        request.setMemo("자기 자신에게 송금");

        assertThatThrownBy(() -> {
            transferService.transfer(SENDER_PHONE, request);
        }).isInstanceOf(BusinessException.class)
          .hasMessageContaining("SAME_ACCOUNT_TRANSFER");
    }

    @Test
    @DisplayName("송금 후 잔액 정확성 확인")
    void transfer_shouldUpdateBalanceCorrectly() {
        // Given: 송금자 잔액 100만원, 수신자 잔액 0원
        BigDecimal senderInitialBalance = balanceService.getBalance(SENDER_ACCOUNT);
        BigDecimal receiverInitialBalance = balanceService.getBalance(RECEIVER_ACCOUNT);

        // When: 50만원 송금
        TransferRequest request = new TransferRequest();
        request.setReceiverAccountNumber(RECEIVER_ACCOUNT);
        request.setAmount(new BigDecimal("500000"));
        request.setMemo("잔액 정확성 테스트");

        TransferResponse response = transferService.transfer(SENDER_PHONE, request);

        // Then: 송금 성공 및 잔액 정확히 변경
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        
        BigDecimal senderFinalBalance = balanceService.getBalance(SENDER_ACCOUNT);
        BigDecimal receiverFinalBalance = balanceService.getBalance(RECEIVER_ACCOUNT);

        // 검증: 송금자 잔액이 정확히 차감되었는지
        assertThat(senderFinalBalance).isEqualTo(senderInitialBalance.subtract(new BigDecimal("500000")));
        
        // 검증: 수신자 잔액이 정확히 증가했는지
        assertThat(receiverFinalBalance).isEqualTo(receiverInitialBalance.add(new BigDecimal("500000")));
    }

    @Test
    @DisplayName("대량 동시 송금 시 성공률 확인")
    void multipleConcurrentTransfers_shouldHaveReasonableSuccessRate() throws InterruptedException {
        // Given: 송금자 잔액 100만원
        BigDecimal initialBalance = balanceService.getBalance(SENDER_ACCOUNT);

        // When: 10개의 동시 송금 요청 (각 5만원씩)
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    TransferRequest request = new TransferRequest();
                    request.setReceiverAccountNumber(RECEIVER_ACCOUNT);
                    request.setAmount(new BigDecimal("50000")); // 5만원씩
                    request.setMemo("대량 송금 테스트 " + index);

                    TransferResponse response = transferService.transfer(SENDER_PHONE, request);
                    if ("COMPLETED".equals(response.getStatus())) {
                        successCount.incrementAndGet();
                        log.info("송금 {} 성공", index);
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.warn("송금 {} 실패: {}", index, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: 결과 확인
        log.info("대량 송금 테스트 결과 - 성공: {}, 실패: {}", successCount.get(), failureCount.get());
        
        BigDecimal finalBalance = balanceService.getBalance(SENDER_ACCOUNT);
        log.info("송금자 최종 잔액: {}", finalBalance);

        // 검증: 성공한 송금만큼 잔액이 차감되었는지
        BigDecimal expectedBalance = initialBalance.subtract(
            new BigDecimal("50000").multiply(BigDecimal.valueOf(successCount.get()))
        );
        assertThat(finalBalance).isEqualTo(expectedBalance);
        
        // 검증: 성공률이 합리적인지 (락으로 보호되므로 대부분 성공해야 함)
        assertThat(successCount.get()).isGreaterThan(threadCount / 2); // 최소 50% 이상 성공
    }
}
