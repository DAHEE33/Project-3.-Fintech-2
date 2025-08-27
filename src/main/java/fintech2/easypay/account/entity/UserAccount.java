package fintech2.easypay.account.entity;

import fintech2.easypay.common.enums.AccountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사용자별 다중 계좌 관리를 위한 엔티티
 * 한 사용자가 여러 개의 EasyPay 계좌를 가질 수 있음
 */
@Entity
@Table(name = "user_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAccount {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "account_number", unique = true, nullable = false, length = 20)
    private String accountNumber;
    
    @Column(name = "account_name", nullable = false, length = 50)
    private String accountName; // 계좌 별칭 (예: "용돈계좌", "저축계좌" 등)
    
    @Column(name = "balance", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;
    
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false; // 기본 계좌 여부
    
    @Column(name = "daily_limit", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal dailyLimit = new BigDecimal("10000000"); // 일일 이체 한도 (1천만원)
    
    @Column(name = "monthly_limit", precision = 15, scale = 2) 
    @Builder.Default
    private BigDecimal monthlyLimit = new BigDecimal("100000000"); // 월 이체 한도 (1억원)
    
    @Version
    private Integer version; // 낙관적 락
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // 비즈니스 메서드
    
    /**
     * 잔액 확인
     */
    public boolean hasEnoughBalance(BigDecimal amount) {
        return this.balance.compareTo(amount) >= 0;
    }
    
    /**
     * 출금
     */
    public void withdraw(BigDecimal amount) {
        if (!hasEnoughBalance(amount)) {
            throw new IllegalArgumentException("잔액 부족: 현재 잔액 " + this.balance + ", 요청 금액 " + amount);
        }
        this.balance = this.balance.subtract(amount);
    }
    
    /**
     * 입금
     */
    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("입금 금액은 0보다 커야 합니다: " + amount);
        }
        this.balance = this.balance.add(amount);
    }
    
    /**
     * 계좌 활성화 여부 확인
     */
    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }
    
    /**
     * 기본 계좌로 설정
     */
    public void setPrimary() {
        this.isPrimary = true;
    }
    
    /**
     * 기본 계좌 해제
     */
    public void unsetPrimary() {
        this.isPrimary = false;
    }
    
    /**
     * 계좌 비활성화
     */
    public void deactivate() {
        this.status = AccountStatus.INACTIVE;
    }
    
    /**
     * 계좌 활성화
     */
    public void activate() {
        this.status = AccountStatus.ACTIVE;
    }
}