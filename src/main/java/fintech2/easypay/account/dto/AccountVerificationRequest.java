package fintech2.easypay.account.dto;

import lombok.Data;

@Data
public class AccountVerificationRequest {
    private String accountNumber;
    private String bankName;
}