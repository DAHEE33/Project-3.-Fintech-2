package fintech2.easypay.auth.controller;

import fintech2.easypay.auth.dto.*;
import fintech2.easypay.auth.service.PinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/pin")
@RequiredArgsConstructor
public class PinController {

    private final PinService pinService;

    /**
     * PIN 등록 (최초 설정)
     * POST /pin/register
     */
    @PostMapping("/register")
    public ResponseEntity<PinResponse> registerPin(
            @RequestBody PinRequest request,
            Authentication authentication) {
        
        String phoneNumber = authentication.getName();
        log.info("PIN 등록 요청: phoneNumber={}", phoneNumber);
        
        PinResponse response = pinService.registerPin(phoneNumber, request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * PIN 검증
     * POST /pin/verify
     */
    @PostMapping("/verify")
    public ResponseEntity<PinResponse> verifyPin(
            @RequestBody PinVerifyRequest request,
            Authentication authentication) {
        
        String phoneNumber = authentication.getName();
        log.info("PIN 검증 요청: phoneNumber={}, purpose={}", phoneNumber, request.getPurpose());
        
        PinResponse response = pinService.verifyPin(phoneNumber, request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else if (response.isPinLocked()) {
            return ResponseEntity.status(423).body(response); // 423 Locked
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * PIN 변경
     * PUT /pin/change
     */
    @PutMapping("/change")
    public ResponseEntity<PinResponse> changePin(
            @RequestBody ChangePinRequest request,
            Authentication authentication) {
        
        String phoneNumber = authentication.getName();
        log.info("PIN 변경 요청: phoneNumber={}", phoneNumber);
        
        PinResponse response = pinService.changePin(phoneNumber, request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else if (response.isPinLocked()) {
            return ResponseEntity.status(423).body(response); // 423 Locked
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * PIN 상태 조회
     * GET /pin/status
     */
    @GetMapping("/status")
    public ResponseEntity<PinResponse> getPinStatus(Authentication authentication) {
        
        String phoneNumber = authentication.getName();
        log.info("PIN 상태 조회: phoneNumber={}", phoneNumber);
        
        PinResponse response = pinService.getPinStatus(phoneNumber);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * PIN 세션 토큰 검증 (내부 API - 다른 서비스에서 사용)
     * POST /pin/validate-session
     */
    @PostMapping("/validate-session")
    public ResponseEntity<Boolean> validatePinSession(
            @RequestParam String sessionToken,
            @RequestParam String purpose) {
        
        log.info("PIN 세션 토큰 검증: purpose={}", purpose);
        
        boolean isValid = pinService.validatePinSessionToken(sessionToken, purpose);
        return ResponseEntity.ok(isValid);
    }
}