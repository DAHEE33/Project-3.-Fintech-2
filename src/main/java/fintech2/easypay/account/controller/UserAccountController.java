package fintech2.easypay.account.controller;

import fintech2.easypay.account.entity.UserAccount;
import fintech2.easypay.account.service.UserAccountService;
import fintech2.easypay.auth.dto.UserPrincipal;
import fintech2.easypay.auth.service.PinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 다중 계좌 관리 컨트롤러
 */
@RestController
@RequestMapping("/api/user-accounts")
@RequiredArgsConstructor
@Slf4j
public class UserAccountController {
    
    private final UserAccountService userAccountService;
    private final PinService pinService;
    
    /**
     * 사용자의 모든 계좌 조회
     */
    @GetMapping
    public ResponseEntity<?> getUserAccounts(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            List<UserAccount> accounts = userAccountService.getUserAccounts(userPrincipal.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("accounts", accounts);
            response.put("totalCount", accounts.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("계좌 목록 조회 실패: userId={}", userPrincipal.getId(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "계좌 목록 조회 중 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 새로운 EasyPay 계좌 생성 (PIN 인증 필요)
     */
    @PostMapping("/create")
    public ResponseEntity<?> createNewAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            String accountName = request.get("accountName");
            String pin = request.get("pin");
            
            if (accountName == null || accountName.trim().isEmpty()) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "계좌 이름을 입력해주세요"
                ));
            }
            
            if (pin == null || pin.length() != 6) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "INVALID_PIN",
                    "message", "PIN 번호 6자리를 입력해주세요"
                ));
            }
            
            // PIN 검증
            boolean pinValid = pinService.verifyPin(userPrincipal.getId(), pin);
            if (!pinValid) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "INVALID_PIN",
                    "message", "PIN 번호가 일치하지 않습니다"
                ));
            }
            
            // 새 계좌 생성
            UserAccount newAccount = userAccountService.createNewAccount(
                userPrincipal.getId(), accountName.trim(), pin);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "새 계좌가 생성되었습니다");
            response.put("account", newAccount);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of(
                "error", "ACCOUNT_LIMIT_EXCEEDED",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("계좌 생성 실패: userId={}", userPrincipal.getId(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "계좌 생성 중 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 계좌 별칭 변경
     */
    @PutMapping("/{accountNumber}/name")
    public ResponseEntity<?> updateAccountName(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String accountNumber,
            @RequestBody Map<String, String> request) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            String newAccountName = request.get("accountName");
            
            if (newAccountName == null || newAccountName.trim().isEmpty()) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "새로운 계좌 이름을 입력해주세요"
                ));
            }
            
            UserAccount updatedAccount = userAccountService.updateAccountName(
                userPrincipal.getId(), accountNumber, newAccountName.trim());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "계좌 별칭이 변경되었습니다");
            response.put("account", updatedAccount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("계좌 별칭 변경 실패: userId={}, accountNumber={}", 
                     userPrincipal.getId(), accountNumber, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "계좌 별칭 변경 중 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 기본 계좌 변경
     */
    @PutMapping("/primary")
    public ResponseEntity<?> changePrimaryAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            String newPrimaryAccountNumber = request.get("accountNumber");
            
            if (newPrimaryAccountNumber == null || newPrimaryAccountNumber.trim().isEmpty()) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "계좌번호를 입력해주세요"
                ));
            }
            
            userAccountService.changePrimaryAccount(userPrincipal.getId(), newPrimaryAccountNumber);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "기본 계좌가 변경되었습니다");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("기본 계좌 변경 실패: userId={}", userPrincipal.getId(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "기본 계좌 변경 중 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 계좌 비활성화
     */
    @DeleteMapping("/{accountNumber}")
    public ResponseEntity<?> deactivateAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String accountNumber,
            @RequestBody Map<String, String> request) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            String pin = request.get("pin");
            
            if (pin == null || pin.length() != 6) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "INVALID_PIN",
                    "message", "PIN 번호 6자리를 입력해주세요"
                ));
            }
            
            // PIN 검증
            boolean pinValid = pinService.verifyPin(userPrincipal.getId(), pin);
            if (!pinValid) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "INVALID_PIN",
                    "message", "PIN 번호가 일치하지 않습니다"
                ));
            }
            
            userAccountService.deactivateAccount(userPrincipal.getId(), accountNumber);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "계좌가 비활성화되었습니다");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of(
                "error", "INVALID_OPERATION",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("계좌 비활성화 실패: userId={}, accountNumber={}", 
                     userPrincipal.getId(), accountNumber, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "계좌 비활성화 중 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 계좌 통계 조회
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getAccountStatistics(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            UserAccountService.AccountStatistics statistics = 
                userAccountService.getUserAccountStatistics(userPrincipal.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("statistics", statistics);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("계좌 통계 조회 실패: userId={}", userPrincipal.getId(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "계좌 통계 조회 중 오류가 발생했습니다"
            ));
        }
    }
}