package fintech2.easypay.auth.controller;

import fintech2.easypay.auth.dto.LoginRequest;
import fintech2.easypay.auth.dto.PasswordVerifyRequest;
import fintech2.easypay.auth.dto.RegisterRequest;
import fintech2.easypay.auth.dto.UserUpdateRequest;
import fintech2.easypay.auth.dto.TokenRefreshRequest;
import fintech2.easypay.auth.service.AuthService;
import fintech2.easypay.auth.service.TokenService;
import fintech2.easypay.auth.dto.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final TokenService tokenService;
    
    @Value("${jwt.check.access:5000}")
    private int accessCheckInterval;
    
    @Value("${jwt.check.background:10000}")
    private int backgroundCheckInterval;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody TokenRefreshRequest req) {
        try {
            TokenService.TokenPair tokenPair = tokenService.refreshAccessToken(req.getRefreshToken());
            
            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", tokenPair.getAccessToken());
            response.put("refreshToken", tokenPair.getRefreshToken());
            response.put("message", "토큰이 성공적으로 갱신되었습니다");
            
            return ResponseEntity.ok(response);
        } catch (fintech2.easypay.common.exception.AuthException e) {
            // AuthException은 구체적인 에러 코드를 포함
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getErrorCode());
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            // 기타 예외는 일반적인 에러로 처리
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "TOKEN_REFRESH_FAILED");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }



    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmailDuplicate(@RequestParam String email) {
        return authService.checkEmailDuplicate(email);
    }
    
    @GetMapping("/check-phone")
    public ResponseEntity<?> checkPhoneDuplicate(@RequestParam String phoneNumber) {
        return authService.checkPhoneDuplicate(phoneNumber);
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        return authService.getProfile();
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UserUpdateRequest req) {
        return authService.updateProfile(req);
    }

    @PostMapping("/verify-password")
    public ResponseEntity<?> verifyPassword(@RequestBody PasswordVerifyRequest req) {
        return authService.verifyPassword(req);
    }

    @GetMapping("/check-pin-required")
    public ResponseEntity<?> checkPinRequired() {
        return authService.checkPinRequired();
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication authentication) {
        try {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            tokenService.revokeAllUserTokens(userPrincipal.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "로그아웃이 완료되었습니다");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "LOGOUT_FAILED");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/client-config")
    public ResponseEntity<?> getClientConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("accessCheckInterval", accessCheckInterval);  // 5초
        config.put("backgroundCheckInterval", backgroundCheckInterval);  // 10초
        return ResponseEntity.ok(config);
    }
} 