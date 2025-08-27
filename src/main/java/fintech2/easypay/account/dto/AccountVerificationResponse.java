package fintech2.easypay.account.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountVerificationResponse {
    private boolean valid;
    private String accountHolderName;
    private String bankName;
    private String message;
    
    public static AccountVerificationResponse success(String accountHolderName, String bankName) {
        return new AccountVerificationResponse(true, accountHolderName, bankName, "계좌 확인 완료");
    }
    
    public static AccountVerificationResponse failure(String message) {
        return new AccountVerificationResponse(false, null, null, message);
    }
}