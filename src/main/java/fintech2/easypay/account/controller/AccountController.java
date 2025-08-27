package fintech2.easypay.account.controller;

import fintech2.easypay.account.entity.UserAccount;
import fintech2.easypay.account.service.AccountService;
import fintech2.easypay.account.service.UserAccountService;
import fintech2.easypay.auth.dto.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {
    private final AccountService accountService;
    private final UserAccountService userAccountService;

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String accountNumber, @RequestHeader("Authorization") String token) {
        return accountService.getBalance(accountNumber, token);
    }

    @GetMapping
    public ResponseEntity<?> getMyAccount(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        return accountService.getMyAccount(userPrincipal);
    }

    @GetMapping("/balance")
    public ResponseEntity<?> getMyBalance(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        return accountService.getMyBalance(userPrincipal);
    }

    @GetMapping("/debug")
    public ResponseEntity<?> debug(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("userPrincipal", userPrincipal);
        if (userPrincipal != null) {
            debugInfo.put("accountNumber", userPrincipal.getAccountNumber());
            debugInfo.put("phoneNumber", userPrincipal.getPhoneNumber());
            debugInfo.put("id", userPrincipal.getId());
        }
        return ResponseEntity.ok(debugInfo);
    }

    @GetMapping("/test-balance/{accountNumber}")
    public ResponseEntity<?> testBalance(@PathVariable String accountNumber) {
        try {
            return accountService.getBalance(accountNumber, "test-token");
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getClass().getSimpleName());
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping("/update-balance")
    public ResponseEntity<?> updateBalance(@RequestBody Map<String, Object> request, 
                                         @AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            // 사용자 자신의 계좌번호 사용
            String accountNumber = userPrincipal.getAccountNumber();
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            String transactionType = (String) request.get("transactionType");
            String description = (String) request.get("description");
            
            String userId = userPrincipal.getId().toString();
            
            return accountService.updateBalance(accountNumber, amount, transactionType, description, userId);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "BALANCE_UPDATE_FAILED");
            error.put("message", "잔액 업데이트 실행");
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody Map<String, Object> request,
                                   @AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.status(400).body(Map.of("error", "INVALID_AMOUNT", "message", "입금 금액은 0보다 커야 합니다"));
            }
            
            String memo = request.get("memo") != null ? request.get("memo").toString() : "테스트 입금";
            
            // UserAccountService를 통해 기본 계좌에 입금
            UserAccount updatedAccount = userAccountService.depositToPrimaryAccount(userPrincipal.getId(), amount, memo);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "입금이 완료되었습니다");
            response.put("balance", updatedAccount.getBalance());
            response.put("accountNumber", updatedAccount.getAccountNumber());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("error", "INVALID_REQUEST", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("입금 처리 오류: userId={}", userPrincipal.getId(), e);
            return ResponseEntity.status(500).body(Map.of("error", "DEPOSIT_FAILED", "message", "입금 처리 중 오류가 발생했습니다"));
        }
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody Map<String, Object> request,
                                    @AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.status(400).body(Map.of("error", "INVALID_AMOUNT", "message", "출금 금액은 0보다 커야 합니다"));
            }
            
            String memo = request.get("memo") != null ? request.get("memo").toString() : "테스트 출금";
            
            // UserAccountService를 통해 기본 계좌에서 출금
            UserAccount updatedAccount = userAccountService.withdrawFromPrimaryAccount(userPrincipal.getId(), amount, memo);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "출금이 완료되었습니다");
            response.put("balance", updatedAccount.getBalance());
            response.put("accountNumber", updatedAccount.getAccountNumber());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("error", "INVALID_REQUEST", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("출금 처리 오류: userId={}", userPrincipal.getId(), e);
            return ResponseEntity.status(500).body(Map.of("error", "WITHDRAW_FAILED", "message", "출금 처리 중 오류가 발생했습니다"));
        }
    }

    @GetMapping("/{accountNumber}/transactions")
    public ResponseEntity<?> getTransactionHistory(@PathVariable String accountNumber, @RequestHeader("Authorization") String token) {
        return accountService.getTransactionHistory(accountNumber);
    }
    
    @PostMapping("/sync-account-balance")
    public ResponseEntity<?> syncAccountBalance(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            return accountService.syncAccountBalance(userPrincipal.getAccountNumber());
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "SYNC_FAILED");
            error.put("message", "Account 엔티티 동기화 실패");
            return ResponseEntity.status(500).body(error);
        }
    }
} 