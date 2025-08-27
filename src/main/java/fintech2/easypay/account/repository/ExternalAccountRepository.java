package fintech2.easypay.account.repository;

import fintech2.easypay.account.entity.ExternalAccount;
import fintech2.easypay.account.entity.ExternalAccount.ExternalAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalAccountRepository extends JpaRepository<ExternalAccount, Long> {
    
    /**
     * 사용자의 모든 활성 외부 계좌 조회
     */
    List<ExternalAccount> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(Long userId);
    
    /**
     * 사용자의 인증된 외부 계좌 조회
     */
    List<ExternalAccount> findByUserIdAndVerificationStatusAndIsActiveTrueOrderByCreatedAtDesc(
            Long userId, ExternalAccountStatus status);
    
    /**
     * 계좌번호와 은행코드로 외부 계좌 조회
     */
    Optional<ExternalAccount> findByAccountNumberAndBankCode(String accountNumber, String bankCode);
    
    /**
     * 사용자가 해당 계좌를 이미 등록했는지 확인
     */
    boolean existsByUserIdAndAccountNumberAndBankCodeAndIsActiveTrue(
            Long userId, String accountNumber, String bankCode);
    
    /**
     * 재인증이 필요한 계좌들 조회 (30일 이상 지난 계좌)
     */
    @Query("SELECT ea FROM ExternalAccount ea WHERE ea.verificationStatus = :status " +
           "AND ea.isActive = true AND ea.lastVerifiedAt < :cutoffDate")
    List<ExternalAccount> findAccountsNeedingReVerification(
            @Param("status") ExternalAccountStatus status, 
            @Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 사용자의 특정 은행 계좌 조회
     */
    List<ExternalAccount> findByUserIdAndBankCodeAndIsActiveTrueOrderByCreatedAtDesc(
            Long userId, String bankCode);
    
    /**
     * 인증 실패 횟수가 많은 계좌들 조회
     */
    @Query("SELECT ea FROM ExternalAccount ea WHERE ea.verificationFailureCount >= :failureCount " +
           "AND ea.isActive = true")
    List<ExternalAccount> findAccountsWithHighFailureCount(@Param("failureCount") Integer failureCount);
    
    /**
     * API 제공자별 계좌 조회
     */
    List<ExternalAccount> findByApiProviderAndIsActiveTrueOrderByCreatedAtDesc(String apiProvider);
}