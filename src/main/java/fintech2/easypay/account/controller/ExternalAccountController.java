package fintech2.easypay.account.controller;

import fintech2.easypay.account.entity.ExternalAccount;
import fintech2.easypay.account.service.ExternalAccountService;
import fintech2.easypay.auth.dto.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 외부 계좌 연동 관리 컨트롤러
 */
@RestController
@RequestMapping("/api/external-accounts")
@RequiredArgsConstructor
@Slf4j
public class ExternalAccountController {
    
    private final ExternalAccountService externalAccountService;
    
    /**
     * 사용자의 모든 외부 계좌 조회
     */
    @GetMapping
    public ResponseEntity<?> getUserExternalAccounts(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            List<ExternalAccount> accounts = externalAccountService.getUserExternalAccounts(userPrincipal.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("accounts", accounts);
            response.put("totalCount", accounts.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("외부 계좌 목록 조회 실패: userId={}", userPrincipal.getId(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "외부 계좌 목록 조회 중 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 인증된 외부 계좌만 조회
     */
    @GetMapping("/verified")
    public ResponseEntity<?> getVerifiedExternalAccounts(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            List<ExternalAccount> accounts = externalAccountService.getVerifiedExternalAccounts(userPrincipal.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("accounts", accounts);
            response.put("totalCount", accounts.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("인증된 외부 계좌 조회 실패: userId={}", userPrincipal.getId(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "인증된 외부 계좌 조회 중 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 외부 계좌 등록
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerExternalAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            String accountNumber = request.get("accountNumber");
            String bankCode = request.get("bankCode");
            String bankName = request.get("bankName");
            String accountAlias = request.get("accountAlias");
            
            // 입력값 검증
            if (accountNumber == null || accountNumber.trim().isEmpty()) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "계좌번호를 입력해주세요"
                ));
            }
            
            if (bankCode == null || bankCode.trim().isEmpty()) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "은행코드를 선택해주세요"
                ));
            }
            
            if (bankName == null || bankName.trim().isEmpty()) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "은행명을 입력해주세요"
                ));
            }
            
            // 계좌 별칭이 없으면 은행명으로 설정
            if (accountAlias == null || accountAlias.trim().isEmpty()) {
                accountAlias = bankName + " 계좌";
            }
            
            ExternalAccount registeredAccount = externalAccountService.registerExternalAccount(
                userPrincipal.getId(), 
                accountNumber.trim(), 
                bankCode.trim(), 
                bankName.trim(), 
                accountAlias.trim()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "외부 계좌가 등록되었습니다");
            response.put("account", registeredAccount);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of(
                "error", "INVALID_OPERATION",
                "message", e.getMessage()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of(
                "error", "VERIFICATION_FAILED",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("외부 계좌 등록 실패: userId={}", userPrincipal.getId(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "외부 계좌 등록 중 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 외부 계좌 재검증
     */
    @PostMapping("/{accountId}/re-verify")
    public ResponseEntity<?> reVerifyExternalAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountId) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            ExternalAccount reVerifiedAccount = externalAccountService.reVerifyExternalAccount(
                userPrincipal.getId(), accountId);
            
            Map<String, Object> response = new HashMap<>();
            
            if (reVerifiedAccount.getVerificationStatus() == ExternalAccount.ExternalAccountStatus.VERIFIED) {
                response.put("success", true);
                response.put("message", "계좌 재검증이 완료되었습니다");
            } else {
                response.put("success", false);
                response.put("message", "계좌 재검증에 실패했습니다");
            }
            
            response.put("account", reVerifiedAccount);
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of(
                "error", "INVALID_OPERATION",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("외부 계좌 재검증 실패: userId={}, accountId={}", userPrincipal.getId(), accountId, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "외부 계좌 재검증 중 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 외부 계좌 별칭 변경
     */
    @PutMapping("/{accountId}/alias")
    public ResponseEntity<?> updateAccountAlias(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountId,
            @RequestBody Map<String, String> request) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            String newAlias = request.get("accountAlias");
            
            if (newAlias == null || newAlias.trim().isEmpty()) {
                return ResponseEntity.status(400).body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", "새로운 계좌 별칭을 입력해주세요"
                ));
            }
            
            ExternalAccount updatedAccount = externalAccountService.updateAccountAlias(
                userPrincipal.getId(), accountId, newAlias.trim());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "계좌 별칭이 변경되었습니다");
            response.put("account", updatedAccount);
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of(
                "error", "INVALID_OPERATION",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("외부 계좌 별칭 변경 실패: userId={}, accountId={}", userPrincipal.getId(), accountId, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "외부 계좌 별칭 변경 중 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 외부 계좌 삭제
     */
    @DeleteMapping("/{accountId}")
    public ResponseEntity<?> deleteExternalAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long accountId) {
        
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            externalAccountService.deleteExternalAccount(userPrincipal.getId(), accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "외부 계좌가 삭제되었습니다");
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of(
                "error", "INVALID_OPERATION",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("외부 계좌 삭제 실패: userId={}, accountId={}", userPrincipal.getId(), accountId, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "외부 계좌 삭제 중 오류가 발생했습니다"
            ));
        }
    }
    
    /**
     * 외부 계좌 통계 조회
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getExternalAccountStatistics(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
        }
        
        try {
            ExternalAccountService.ExternalAccountStatistics statistics = 
                externalAccountService.getUserExternalAccountStatistics(userPrincipal.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("statistics", statistics);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("외부 계좌 통계 조회 실패: userId={}", userPrincipal.getId(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "외부 계좌 통계 조회 중 오류가 발생했습니다"
            ));
        }
    }
}