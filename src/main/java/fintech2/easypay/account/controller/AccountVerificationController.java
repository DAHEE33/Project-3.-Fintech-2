package fintech2.easypay.account.controller;

import fintech2.easypay.account.dto.AccountVerificationRequest;
import fintech2.easypay.account.dto.AccountVerificationResponse;
import fintech2.easypay.account.service.AccountVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountVerificationController {

    private final AccountVerificationService accountVerificationService;

    @PostMapping("/verify")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> verifyAccount(@RequestBody AccountVerificationRequest request) {
        
        log.info("계좌 검증 API 호출: 계좌번호={}, 은행={}", request.getAccountNumber(), request.getBankName());
        
        try {
            // 입력값 검증
            if (request.getAccountNumber() == null || request.getAccountNumber().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "계좌번호를 입력해주세요."));
            }
            
            if (request.getBankName() == null || request.getBankName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "은행을 선택해주세요."));
            }
            
            AccountVerificationResponse response = accountVerificationService.verifyAccount(request);
            
            if (response.isValid()) {
                log.info("계좌 검증 성공: 계좌번호={}, 소유자={}", request.getAccountNumber(), response.getAccountHolderName());
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "계좌 검증 성공",
                    "data", response
                ));
            } else {
                log.warn("계좌 검증 실패: 계좌번호={}, 사유={}", request.getAccountNumber(), response.getMessage());
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", response.getMessage()));
            }
            
        } catch (Exception e) {
            log.error("계좌 검증 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", "계좌 검증 중 오류가 발생했습니다."));
        }
    }
}