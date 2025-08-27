package fintech2.easypay.external.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 외부 은행 API 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankApiRequest {
    
    private String accountNumber;
    private String bankCode;
    private String verificationMethod; // "REAL_NAME", "SMS", "PIN" 등
    private String requestId; // 요청 추적 ID
    private String clientId; // 클라이언트 식별자
    
    // 추가 검증 정보 (은행별로 다를 수 있음)
    private String accountHolderName; // 예금주명 (선택적)
    private String birthDate; // 생년월일 (선택적)
    private String phoneNumber; // 휴대폰번호 (선택적)
}