package fintech2.easypay.transfer.entity;

import fintech2.easypay.common.BaseEntity;
import fintech2.easypay.auth.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfers")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transfer extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "transfer_id", unique = true, nullable = false, length = 255)
    private String transferId;
    
    @Column(name = "transaction_id", unique = true, nullable = false, length = 255)
    private String transactionId;
    
    @Column(name = "bank_transaction_id")
    private String bankTransactionId; // 외부 은행 시스템의 거래 ID
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false) // V1 스키마와 일치
    private User sender; // Member -> User
    
    @Column(name = "sender_account_number", nullable = false, length = 255)
    private String senderAccountNumber;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false) // V1 스키마와 일치
    private User receiver; // Member -> User
    
    @Column(name = "receiver_account_number", nullable = false, length = 255)
    private String receiverAccountNumber;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "description", columnDefinition = "TEXT") // memo -> description으로 변경
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TransferStatus status = TransferStatus.REQUESTED;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "failed_reason", columnDefinition = "TEXT")
    private String failedReason;
    
    public void markAsProcessing() {
        this.status = TransferStatus.PROCESSING;
    }
    
    public void markAsCompleted() {
        this.status = TransferStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }
    
    public void setBankTransactionId(String bankTransactionId) {
        this.bankTransactionId = bankTransactionId;
    }
    
    public void markAsFailed(String reason) {
        this.status = TransferStatus.FAILED;
        this.failedReason = reason;
        this.processedAt = LocalDateTime.now();
    }
    
    public void markAsCancelled() {
        this.status = TransferStatus.CANCELLED;
        this.processedAt = LocalDateTime.now();
    }
    
    public void markAsTimeout(String reason) {
        this.status = TransferStatus.TIMEOUT;
        this.failedReason = reason;
        this.processedAt = LocalDateTime.now();
    }
    
    public void markAsUnknown(String reason) {
        this.status = TransferStatus.UNKNOWN;
        this.failedReason = reason;
        this.processedAt = LocalDateTime.now();
    }
    
    public boolean isCompleted() {
        return this.status == TransferStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return this.status == TransferStatus.FAILED;
    }
    
    public boolean isCancelled() {
        return this.status == TransferStatus.CANCELLED;
    }
    
    public boolean isTimeout() {
        return this.status == TransferStatus.TIMEOUT;
    }
    
    public boolean isUnknown() {
        return this.status == TransferStatus.UNKNOWN;
    }
    
    public boolean isPendingConfirmation() {
        return this.status == TransferStatus.TIMEOUT || this.status == TransferStatus.UNKNOWN;
    }
}
