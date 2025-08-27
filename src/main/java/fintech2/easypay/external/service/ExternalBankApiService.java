package fintech2.easypay.external.service;

import fintech2.easypay.external.dto.BankApiRequest;
import fintech2.easypay.external.dto.BankApiResponse;
import fintech2.easypay.external.dto.AccountVerificationRequest;
import fintech2.easypay.external.dto.AccountVerificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.util.HashMap;
import java.util.Map;

/**
 * 외부 은행 API 연동 서비스
 * 실제 환경에서는 각 은행별 API 스펙에 따라 구현 필요
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalBankApiService {
    
    private final RestTemplate restTemplate;
    
    @Value("${external.banking.api.base-url:https://api.banking.example.com}")
    private String bankingApiBaseUrl;
    
    @Value("${external.banking.api.timeout:30000}")
    private int apiTimeout;
    
    @Value("${external.banking.api.key:}")
    private String apiKey;
    
    // 은행별 API 엔드포인트 매핑
    private final Map<String, String> bankApiEndpoints = Map.of(
        "004", "/kb/account/verify",           // KB국민은행
        "088", "/shinhan/account/verify",      // 신한은행
        "020", "/woori/account/verify",        // 우리은행
        "081", "/hana/account/verify",         // 하나은행
        "090", "/kakao/account/verify",        // 카카오뱅크
        "092", "/toss/account/verify"          // 토스뱅크
    );
    
    /**
     * 계좌 정보 검증
     */
    public AccountVerificationResponse verifyAccount(AccountVerificationRequest request) {
        log.info("외부 계좌 검증 요청: 은행코드={}, 계좌번호={}", request.getBankCode(), 
                 maskAccountNumber(request.getAccountNumber()));
        
        try {
            // 현재는 Mock 데이터로 응답 (실제로는 각 은행 API 호출)
            return verifyAccountMock(request);
            
            // 실제 API 호출 코드 (주석 처리)
            // return callBankApi(request);
            
        } catch (Exception e) {
            log.error("외부 계좌 검증 실패: {}", e.getMessage(), e);
            return AccountVerificationResponse.builder()
                    .success(false)
                    .errorCode("API_ERROR")
                    .errorMessage("계좌 검증 중 오류가 발생했습니다")
                    .build();
        }
    }
    
    /**
     * Mock 계좌 검증 (테스트용)
     */
    private AccountVerificationResponse verifyAccountMock(AccountVerificationRequest request) {
        // 각 은행별 테스트 계좌 데이터
        Map<String, Map<String, String>> mockAccounts = new HashMap<>();
        
        // KB국민은행 (004)
        Map<String, String> kbAccounts = Map.of(
            "123456-04-123456", "김국민",
            "654321-04-654321", "박국민",
            "111111-04-222222", "이국민"
        );
        mockAccounts.put("004", kbAccounts);
        
        // 신한은행 (088)
        Map<String, String> shinhanAccounts = Map.of(
            "110-123-456789", "송신한",
            "110-987-654321", "윤신한",
            "110-555-123456", "최신한"
        );
        mockAccounts.put("088", shinhanAccounts);
        
        // 카카오뱅크 (090)
        Map<String, String> kakaoAccounts = Map.of(
            "3333-01-1234567", "김카카오",
            "3333-01-7654321", "박카카오",
            "3333-01-9999999", "이카카오"
        );
        mockAccounts.put("090", kakaoAccounts);
        
        // 토스뱅크 (092)
        Map<String, String> tossAccounts = Map.of(
            "100-2345-678901", "이토스",
            "100-2345-109876", "최토스",
            "100-1111-222222", "김토스"
        );
        mockAccounts.put("092", tossAccounts);
        
        // Mock 검증 로직
        String bankCode = request.getBankCode();
        String accountNumber = request.getAccountNumber();
        
        Map<String, String> bankAccounts = mockAccounts.get(bankCode);
        if (bankAccounts != null && bankAccounts.containsKey(accountNumber)) {
            String accountHolderName = bankAccounts.get(accountNumber);
            
            log.info("외부 계좌 검증 성공: {}은행 {} ({})", getBankName(bankCode), 
                     maskAccountNumber(accountNumber), accountHolderName);
            
            return AccountVerificationResponse.builder()
                    .success(true)
                    .accountHolderName(accountHolderName)
                    .bankCode(bankCode)
                    .bankName(getBankName(bankCode))
                    .accountNumber(accountNumber)
                    .build();
        } else {
            log.warn("외부 계좌 검증 실패: 계좌를 찾을 수 없음 - {}은행 {}", 
                     getBankName(bankCode), maskAccountNumber(accountNumber));
            
            return AccountVerificationResponse.builder()
                    .success(false)
                    .errorCode("ACCOUNT_NOT_FOUND")
                    .errorMessage("해당 계좌를 찾을 수 없습니다")
                    .build();
        }
    }
    
    /**
     * 실제 은행 API 호출 (구현 예시)
     */
    private AccountVerificationResponse callBankApi(AccountVerificationRequest request) {
        String bankCode = request.getBankCode();
        String endpoint = bankApiEndpoints.get(bankCode);
        
        if (endpoint == null) {
            throw new IllegalArgumentException("지원하지 않는 은행코드: " + bankCode);
        }
        
        String url = bankingApiBaseUrl + endpoint;
        
        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("X-API-Version", "1.0");
        
        // 요청 본문 생성
        BankApiRequest apiRequest = BankApiRequest.builder()
                .accountNumber(request.getAccountNumber())
                .bankCode(request.getBankCode())
                .verificationMethod("REAL_NAME")
                .build();
        
        HttpEntity<BankApiRequest> entity = new HttpEntity<>(apiRequest, headers);
        
        try {
            ResponseEntity<BankApiResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, BankApiResponse.class);
            
            BankApiResponse apiResponse = response.getBody();
            if (apiResponse != null && apiResponse.isSuccess()) {
                return AccountVerificationResponse.builder()
                        .success(true)
                        .accountHolderName(apiResponse.getAccountHolderName())
                        .bankCode(bankCode)
                        .bankName(getBankName(bankCode))
                        .accountNumber(request.getAccountNumber())
                        .build();
            } else {
                return AccountVerificationResponse.builder()
                        .success(false)
                        .errorCode(apiResponse != null ? apiResponse.getErrorCode() : "UNKNOWN")
                        .errorMessage(apiResponse != null ? apiResponse.getErrorMessage() : "알 수 없는 오류")
                        .build();
            }
            
        } catch (ResourceAccessException e) {
            log.error("은행 API 타임아웃: {}", e.getMessage());
            throw new RuntimeException("은행 API 응답 시간 초과");
        } catch (Exception e) {
            log.error("은행 API 호출 오류: {}", e.getMessage(), e);
            throw new RuntimeException("은행 API 호출 중 오류 발생");
        }
    }
    
    /**
     * 은행코드로 은행명 조회
     */
    private String getBankName(String bankCode) {
        Map<String, String> bankNames = Map.of(
            "004", "KB국민은행",
            "088", "신한은행", 
            "020", "우리은행",
            "081", "하나은행",
            "090", "카카오뱅크",
            "092", "토스뱅크",
            "003", "IBK기업은행",
            "011", "NH농협은행",
            "023", "SC제일은행",
            "027", "한국씨티은행"
        );
        
        return bankNames.getOrDefault(bankCode, "알 수 없는 은행");
    }
    
    /**
     * 계좌번호 마스킹
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        
        int length = accountNumber.length();
        String maskedPart = "*".repeat(Math.max(0, length - 4));
        return accountNumber.substring(0, Math.min(2, length)) + 
               maskedPart + 
               accountNumber.substring(Math.max(2, length - 2));
    }
    
    /**
     * API 연결 상태 확인
     */
    public boolean isApiAvailable(String bankCode) {
        try {
            // 실제로는 각 은행 API health check 엔드포인트 호출
            return bankApiEndpoints.containsKey(bankCode);
        } catch (Exception e) {
            log.error("은행 API 상태 확인 실패: {}", e.getMessage());
            return false;
        }
    }
}