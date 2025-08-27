package fintech2.easypay.external.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 외부 은행 API 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankApiResponse {
    
    private boolean success;
    private String responseCode; // 응답 코드
    private String responseMessage; // 응답 메시지
    
    // 성공 시 반환되는 계좌 정보
    private String accountNumber;
    private String accountHolderName;
    private String bankCode;
    private String bankName;
    private String accountStatus; // "ACTIVE", "INACTIVE", "SUSPENDED" 등
    
    // 에러 정보
    private String errorCode;
    private String errorMessage;
    
    // 메타 정보
    private String requestId;
    private LocalDateTime processedAt;
    private String apiVersion;
}