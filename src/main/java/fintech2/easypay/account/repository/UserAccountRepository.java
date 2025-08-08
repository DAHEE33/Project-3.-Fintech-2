package fintech2.easypay.account.repository;

import fintech2.easypay.account.entity.UserAccount;
import fintech2.easypay.common.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    
    /**
     * 사용자의 모든 활성 계좌 조회
     */
    List<UserAccount> findByUserIdAndStatusOrderByIsPrimaryDescCreatedAtAsc(Long userId, AccountStatus status);
    
    /**
     * 사용자의 기본 계좌 조회
     */
    Optional<UserAccount> findByUserIdAndIsPrimaryTrue(Long userId);
    
    /**
     * 계좌번호로 계좌 조회
     */
    Optional<UserAccount> findByAccountNumber(String accountNumber);
    
    /**
     * 사용자의 계좌 개수 조회
     */
    long countByUserIdAndStatus(Long userId, AccountStatus status);
    
    /**
     * 사용자 ID와 계좌번호로 계좌 조회
     */
    Optional<UserAccount> findByUserIdAndAccountNumber(Long userId, String accountNumber);
    
    /**
     * 계좌번호 존재 여부 확인
     */
    boolean existsByAccountNumber(String accountNumber);
    
    /**
     * 사용자의 모든 계좌 조회 (상태 무관)
     */
    List<UserAccount> findByUserIdOrderByIsPrimaryDescCreatedAtAsc(Long userId);
    
    /**
     * 기본 계좌를 제외한 사용자의 계좌 조회
     */
    @Query("SELECT ua FROM UserAccount ua WHERE ua.userId = :userId AND ua.isPrimary = false AND ua.status = :status")
    List<UserAccount> findNonPrimaryAccountsByUserId(@Param("userId") Long userId, @Param("status") AccountStatus status);
}