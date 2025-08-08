package fintech2.easypay.payment.controller;

import fintech2.easypay.auth.dto.UserPrincipal;
import fintech2.easypay.common.ApiResponse;
import fintech2.easypay.payment.dto.PaymentRequest;
import fintech2.easypay.payment.dto.PaymentResponse;
import fintech2.easypay.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {
    
    private final PaymentService paymentService;
    
    /**
     * 결제 처리
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody PaymentRequest request) {
        
        log.info("결제 요청: 사용자={}, 가맹점={}, 금액={}", 
                user.getUsername(), request.getMerchantName(), request.getAmount());
        
        PaymentResponse response = paymentService.processPayment(user.getUsername(), request); // getMember().getPhoneNumber() -> getUsername()
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 결제 취소
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String paymentId,
            @RequestParam(required = false) String reason) {
        
        log.info("결제 취소 요청: 사용자={}, 결제ID={}, 사유={}", 
                user.getUsername(), paymentId, reason);
        
        PaymentResponse response = paymentService.cancelPayment(
                user.getUsername(), paymentId, reason);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 결제 환불
     */
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refundPayment(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String paymentId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String reason) {
        
        log.info("결제 환불 요청: 사용자={}, 결제ID={}, 금액={}, 사유={}", 
                user.getUsername(), paymentId, amount, reason);
        
        PaymentResponse response = paymentService.refundPayment(
                user.getUsername(), paymentId, amount, reason);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 결제 내역 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getPaymentHistory(
            @AuthenticationPrincipal UserPrincipal user,
            Pageable pageable) {
        
        log.info("결제 내역 조회: 사용자={}", user.getUsername());
        
        Page<PaymentResponse> payments = paymentService.getPaymentHistory(
                user.getUsername(), pageable);
        
        return ResponseEntity.ok(ApiResponse.success(payments));
    }
    
    /**
     * 결제 상세 조회
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String paymentId) {
        
        log.info("결제 상세 조회: 사용자={}, 결제ID={}", 
                user.getUsername(), paymentId);
        
        PaymentResponse response = paymentService.getPayment(
                user.getUsername(), paymentId);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}