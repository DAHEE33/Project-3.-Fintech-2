package fintech2.easypay.external.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 계좌 검증 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVerificationResponse {
    
    private boolean success;
    
    // 성공 시 계좌 정보
    private String accountNumber;
    private String accountHolderName;
    private String bankCode;
    private String bankName;
    private String accountStatus; // "ACTIVE", "INACTIVE" 등
    
    // 검증 결과 정보
    private String verificationLevel; // "BASIC", "ENHANCED"
    private LocalDateTime verifiedAt;
    private String verificationId; // 검증 추적 ID
    
    // 실패 시 에러 정보
    private String errorCode;
    private String errorMessage;
    
    // 추가 정보
    private boolean requiresAdditionalVerification; // 추가 인증 필요 여부
    private String nextVerificationMethod; // 다음 인증 방법
    
    /**
     * 성공 응답 생성
     */
    public static AccountVerificationResponse success(String accountNumber, String accountHolderName, 
                                                    String bankCode, String bankName) {
        return AccountVerificationResponse.builder()
                .success(true)
                .accountNumber(accountNumber)
                .accountHolderName(accountHolderName)
                .bankCode(bankCode)
                .bankName(bankName)
                .verifiedAt(LocalDateTime.now())
                .verificationLevel("BASIC")
                .build();
    }
    
    /**
     * 실패 응답 생성
     */
    public static AccountVerificationResponse failure(String errorCode, String errorMessage) {
        return AccountVerificationResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}