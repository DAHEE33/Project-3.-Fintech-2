package fintech2.easypay.external.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 계좌 검증 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVerificationRequest {
    
    private String accountNumber;
    private String bankCode;
    private String bankName;
    
    // 사용자 정보 (선택적 - 추가 검증용)
    private Long userId;
    private String userPhoneNumber;
    
    // 검증 옵션
    private boolean skipCache; // 캐시 건너뛰기 여부
    private String verificationLevel; // "BASIC", "ENHANCED" 등
}