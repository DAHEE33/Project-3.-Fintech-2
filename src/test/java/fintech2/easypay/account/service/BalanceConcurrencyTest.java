package fintech2.easypay.account.service;

import fintech2.easypay.account.entity.AccountBalance;
import fintech2.easypay.account.repository.AccountBalanceRepository;
import fintech2.easypay.common.enums.TransactionType;
import fintech2.easypay.common.exception.InsufficientBalanceException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 잔액 처리 동시성 문제 증명 테스트
 * 현재 BalanceService의 동시성 제어 한계를 보여주는 테스트들
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class BalanceConcurrencyTest {

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private AccountBalanceRepository accountBalanceRepository;

    private static final String TEST_ACCOUNT = "VA1234567890";

    @BeforeEach
    void setUp() {
        // 테스트 전 계좌 초기화
        accountBalanceRepository.findByAccountNumber(TEST_ACCOUNT)
                .ifPresent(accountBalanceRepository::delete);
    }

    @Test
    @DisplayName("동시 출금 시 OptimisticLockException 발생 증명")
    void concurrentWithdraw_shouldThrowOptimisticLockException() throws InterruptedException {
        // Given: 초기 잔액 100만원
        BigDecimal initialBalance = new BigDecimal("1000000");
        balanceService.increase(TEST_ACCOUNT, initialBalance, TransactionType.DEPOSIT, "초기 설정", null, "user1");

        // When: 10개 스레드가 동시에 10만원씩 출금
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticLockExceptionCount = new AtomicInteger(0);
        AtomicInteger otherExceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    balanceService.decrease(TEST_ACCOUNT, new BigDecimal("100000"), 
                        TransactionType.TRANSFER_OUT, "동시 출금 테스트", null, "user1");
                    successCount.incrementAndGet();
                    log.info("출금 성공: 스레드 {}", Thread.currentThread().getName());
                } catch (ObjectOptimisticLockingFailureException e) {
                    optimisticLockExceptionCount.incrementAndGet();
                    log.warn("OptimisticLockException 발생: 스레드 {}", Thread.currentThread().getName());
                } catch (Exception e) {
                    otherExceptionCount.incrementAndGet();
                    log.error("기타 예외 발생: 스레드 {}, 예외: {}", Thread.currentThread().getName(), e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: OptimisticLockException이 발생했는지 확인
        log.info("테스트 결과 - 성공: {}, OptimisticLockException: {}, 기타예외: {}", 
                successCount.get(), optimisticLockExceptionCount.get(), otherExceptionCount.get());

        // 검증: OptimisticLockException이 발생했는지 확인
        assertThat(optimisticLockExceptionCount.get()).isGreaterThan(0);
        
        // 검증: 잔액이 음수가 되지 않았는지 확인
        BigDecimal finalBalance = balanceService.getBalance(TEST_ACCOUNT);
        assertThat(finalBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        
        // 검증: 성공한 출금만큼 차감되었는지 확인
        BigDecimal expectedBalance = initialBalance.subtract(
            new BigDecimal("100000").multiply(BigDecimal.valueOf(successCount.get()))
        );
        assertThat(finalBalance).isEqualTo(expectedBalance);
    }

    @Test
    @DisplayName("잔액 부족 시 즉시 실패 (재시도 없음)")
    void insufficientBalance_shouldFailImmediately() {
        // Given: 잔액 10만원
        balanceService.increase(TEST_ACCOUNT, new BigDecimal("100000"), TransactionType.DEPOSIT, "설정", null, "user1");

        // When & Then: 20만원 출금 시도 시 즉시 실패
        assertThatThrownBy(() -> {
            balanceService.decrease(TEST_ACCOUNT, new BigDecimal("200000"), 
                TransactionType.TRANSFER_OUT, "잔액 부족 테스트", null, "user1");
        }).isInstanceOf(InsufficientBalanceException.class)
          .hasMessageContaining("잔액이 부족합니다");
    }

    @Test
    @DisplayName("동시 입금 시에도 OptimisticLockException 발생 가능")
    void concurrentDeposit_shouldAlsoThrowOptimisticLockException() throws InterruptedException {
        // Given: 초기 잔액 0원
        BigDecimal initialBalance = BigDecimal.ZERO;

        // When: 5개 스레드가 동시에 10만원씩 입금
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticLockExceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    balanceService.increase(TEST_ACCOUNT, new BigDecimal("100000"), 
                        TransactionType.DEPOSIT, "동시 입금 테스트", null, "user1");
                    successCount.incrementAndGet();
                    log.info("입금 성공: 스레드 {}", Thread.currentThread().getName());
                } catch (ObjectOptimisticLockingFailureException e) {
                    optimisticLockExceptionCount.incrementAndGet();
                    log.warn("OptimisticLockException 발생: 스레드 {}", Thread.currentThread().getName());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then: OptimisticLockException이 발생했는지 확인
        log.info("입금 테스트 결과 - 성공: {}, OptimisticLockException: {}", 
                successCount.get(), optimisticLockExceptionCount.get());

        // 검증: OptimisticLockException이 발생했는지 확인
        assertThat(optimisticLockExceptionCount.get()).isGreaterThan(0);
        
        // 검증: 성공한 입금만큼 증가했는지 확인
        BigDecimal finalBalance = balanceService.getBalance(TEST_ACCOUNT);
        BigDecimal expectedBalance = new BigDecimal("100000").multiply(BigDecimal.valueOf(successCount.get()));
        assertThat(finalBalance).isEqualTo(expectedBalance);
    }

    @Test
    @DisplayName("잔액 조회와 업데이트 사이의 Race Condition 증명")
    void raceConditionBetweenReadAndUpdate() throws InterruptedException {
        // Given: 초기 잔액 100만원
        BigDecimal initialBalance = new BigDecimal("1000000");
        balanceService.increase(TEST_ACCOUNT, initialBalance, TransactionType.DEPOSIT, "초기 설정", null, "user1");

        // When: 두 스레드가 거의 동시에 출금
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // 스레드 1: 60만원 출금
        executor.submit(() -> {
            try {
                startLatch.await(); // 동시 시작
                balanceService.decrease(TEST_ACCOUNT, new BigDecimal("600000"), 
                    TransactionType.TRANSFER_OUT, "Race Condition 테스트 1", null, "user1");
                successCount.incrementAndGet();
                log.info("스레드 1 출금 성공");
            } catch (Exception e) {
                exceptionCount.incrementAndGet();
                log.warn("스레드 1 출금 실패: {}", e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });

        // 스레드 2: 50만원 출금 (잔액 부족해야 함)
        executor.submit(() -> {
            try {
                startLatch.await(); // 동시 시작
                balanceService.decrease(TEST_ACCOUNT, new BigDecimal("500000"), 
                    TransactionType.TRANSFER_OUT, "Race Condition 테스트 2", null, "user1");
                successCount.incrementAndGet();
                log.info("스레드 2 출금 성공");
            } catch (Exception e) {
                exceptionCount.incrementAndGet();
                log.warn("스레드 2 출금 실패: {}", e.getMessage());
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown(); // 동시 시작
        endLatch.await();
        executor.shutdown();

        // Then: 결과 확인
        log.info("Race Condition 테스트 결과 - 성공: {}, 실패: {}", successCount.get(), exceptionCount.get());
        
        BigDecimal finalBalance = balanceService.getBalance(TEST_ACCOUNT);
        log.info("최종 잔액: {}", finalBalance);
        
        // 검증: 잔액이 음수가 되지 않았는지
        assertThat(finalBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        
        // 검증: 최소 하나는 성공했는지
        assertThat(successCount.get()).isGreaterThan(0);
    }
}
