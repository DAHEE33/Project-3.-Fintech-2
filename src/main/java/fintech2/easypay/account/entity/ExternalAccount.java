package fintech2.easypay.account.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 외부 계좌 연동 정보를 저장하는 엔티티
 * 사용자가 등록한 외부 은행 계좌 정보 관리
 */
@Entity
@Table(name = "external_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalAccount {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;
    
    @Column(name = "bank_code", nullable = false, length = 10)
    private String bankCode; // 은행 코드 (예: "004" - KB국민은행)
    
    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName; // 은행명 (예: "KB국민은행")
    
    @Column(name = "account_holder_name", nullable = false, length = 50)
    private String accountHolderName; // 예금주명
    
    @Column(name = "account_alias", length = 50)
    private String accountAlias; // 계좌 별칭 (사용자가 설정)
    
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    @Builder.Default
    private ExternalAccountStatus verificationStatus = ExternalAccountStatus.PENDING;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;
    
    @Column(name = "verification_failure_count")
    @Builder.Default
    private Integer verificationFailureCount = 0;
    
    @Column(name = "api_provider", length = 50)
    private String apiProvider; // API 제공자 (예: "OPEN_BANKING", "KAKAO_PAY" 등)
    
    @Column(name = "external_account_id", length = 100)
    private String externalAccountId; // 외부 시스템에서의 계좌 ID
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * 외부 계좌 상태 enum
     */
    public enum ExternalAccountStatus {
        PENDING("인증 대기"),
        VERIFIED("인증 완료"),
        FAILED("인증 실패"),
        EXPIRED("인증 만료"),
        SUSPENDED("일시 중단");
        
        private final String description;
        
        ExternalAccountStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // 비즈니스 메서드
    
    /**
     * 계좌 인증 성공 처리
     */
    public void markAsVerified() {
        this.verificationStatus = ExternalAccountStatus.VERIFIED;
        this.lastVerifiedAt = LocalDateTime.now();
        this.verificationFailureCount = 0;
    }
    
    /**
     * 계좌 인증 실패 처리
     */
    public void markAsVerificationFailed() {
        this.verificationStatus = ExternalAccountStatus.FAILED;
        this.verificationFailureCount++;
        
        // 5회 이상 실패 시 계좌 비활성화
        if (this.verificationFailureCount >= 5) {
            this.isActive = false;
        }
    }
    
    /**
     * 계좌 활성화 여부 확인
     */
    public boolean isUsable() {
        return this.isActive && 
               this.verificationStatus == ExternalAccountStatus.VERIFIED;
    }
    
    /**
     * 인증이 필요한지 확인 (30일 이상 지난 경우)
     */
    public boolean needsReVerification() {
        if (lastVerifiedAt == null) return true;
        return lastVerifiedAt.isBefore(LocalDateTime.now().minusDays(30));
    }
    
    /**
     * 계좌 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    /**
     * 계좌 활성화
     */
    public void activate() {
        this.isActive = true;
        this.verificationFailureCount = 0;
    }
}