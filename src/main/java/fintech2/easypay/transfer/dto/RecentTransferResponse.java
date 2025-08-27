package fintech2.easypay.transfer.dto;

import fintech2.easypay.transfer.entity.Transfer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentTransferResponse {
    private Long id;
    private String receiverName;
    private String receiverPhoneNumber;
    private String receiverAccountNumber;
    private BigDecimal amount;
    private LocalDateTime createdAt;
    private String memo;
    
    public static RecentTransferResponse from(Transfer transfer) {
        return RecentTransferResponse.builder()
                .id(transfer.getId())
                .receiverName(transfer.getReceiver().getName())
                .receiverPhoneNumber(transfer.getReceiver().getPhoneNumber())
                .receiverAccountNumber(transfer.getReceiverAccountNumber())
                .amount(transfer.getAmount())
                .createdAt(transfer.getCreatedAt())
                .memo(transfer.getMemo())
                .build();
    }
}