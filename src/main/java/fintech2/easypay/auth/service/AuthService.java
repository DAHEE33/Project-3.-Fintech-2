package fintech2.easypay.auth.service;

import fintech2.easypay.auth.dto.LoginRequest;
import fintech2.easypay.auth.dto.RegisterRequest;
import fintech2.easypay.auth.dto.UserUpdateRequest;
import fintech2.easypay.auth.dto.PasswordVerifyRequest;
import fintech2.easypay.auth.entity.User;
import fintech2.easypay.auth.repository.UserRepository;
import fintech2.easypay.account.entity.Account;
import fintech2.easypay.account.entity.AccountBalance;
import fintech2.easypay.account.repository.AccountRepository;
import fintech2.easypay.account.repository.AccountBalanceRepository;
import fintech2.easypay.common.enums.AccountStatus;
import fintech2.easypay.audit.service.AuditLogService;
import fintech2.easypay.audit.service.AlarmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final LoginHistoryService loginHistoryService;
    private final AuditLogService auditLogService;
    private final AlarmService alarmService;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^010-\\d{4}-\\d{4}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    
    // SecureRandom 객체를 클래스 필드로 한 번만 생성하여 재사용
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public ResponseEntity<?> checkEmailDuplicate(String email) {
        try {
            // 이메일 형식 검증
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "INVALID_EMAIL", "message", "이메일을 입력해주세요"));
            }
            
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "INVALID_EMAIL", "message", "올바른 이메일 형식이 아닙니다"));
            }
            
            // 이메일 중복 검사
            Optional<User> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "DUPLICATE_EMAIL", "message", "이미 사용 중인 이메일입니다"));
            }
            
            return ResponseEntity.ok(Map.of("message", "사용 가능한 이메일입니다"));
            
        } catch (Exception e) {
            auditLogService.logError(null, "EMAIL_CHECK", "USER", email, "이메일 중복 검사 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "이메일 검사 중 오류가 발생했습니다"));
        }
    }
    
    public ResponseEntity<?> checkPhoneDuplicate(String phoneNumber) {
        try {
            // 휴대폰 번호 형식 검증
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "INVALID_PHONE", "message", "휴대폰 번호를 입력해주세요"));
            }
            
            if (!PHONE_PATTERN.matcher(phoneNumber).matches()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "INVALID_PHONE", "message", "올바른 휴대폰 번호 형식이 아닙니다"));
            }
            
            // 휴대폰 번호 중복 검사 (하이픈 제거 후 검사)
            String phoneWithoutHyphen = phoneNumber.replace("-", "");
            Optional<User> existingUser = userRepository.findByPhoneNumber(phoneWithoutHyphen);
            if (existingUser.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "DUPLICATE_PHONE", "message", "이미 사용 중인 휴대폰 번호입니다"));
            }
            
            return ResponseEntity.ok(Map.of("message", "사용 가능한 휴대폰 번호입니다"));
            
        } catch (Exception e) {
            auditLogService.logError(null, "PHONE_CHECK", "USER", phoneNumber, "휴대폰 번호 중복 검사 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "휴대폰 번호 검사 중 오류가 발생했습니다"));
        }
    }

    @Transactional
    public ResponseEntity<?> register(RegisterRequest req) {
        try {
            // 유효성 검사
            // 1. 휴대폰 번호 중복 체크
            if (userRepository.findByPhoneNumber(req.getPhoneNumber()).isPresent()) {
                auditLogService.logWarning(null, "REGISTER_ATTEMPT", "USER", req.getPhoneNumber(), "중복된 휴대폰 번호로 가입 시도");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "DUPLICATE_PHONE", "message", "이미 가입된 휴대폰 번호입니다"));
            }
            
            // 2. 이메일 필수 검증
            if (req.getEmail() == null || req.getEmail().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "EMAIL_REQUIRED", "message", "이메일은 필수입니다"));
            }
            
            // 3. 이메일 형식 검증
            if (!EMAIL_PATTERN.matcher(req.getEmail()).matches()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "INVALID_EMAIL", "message", "올바른 이메일 형식이 아닙니다"));
            }
            
            // 4. 이메일 중복 체크
            if (userRepository.findByEmail(req.getEmail()).isPresent()) {
                auditLogService.logWarning(null, "REGISTER_ATTEMPT", "USER", req.getEmail(), "중복된 이메일로 가입 시도");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "DUPLICATE_EMAIL", "message", "이미 사용 중인 이메일입니다"));
            }
            
            // 5. 비밀번호 규칙 (회원가입 시에만 8자 이상, 영문+숫자 조합 적용)
            if (req.getPassword() == null || req.getPassword().length() < 6) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "INVALID_PASSWORD", "message", "비밀번호는 6자 이상이어야 합니다"));
            }
            
            // 회원가입 시에만 영문+숫자 조합 검증 (기존 계정과의 호환성을 위해)
            String password = req.getPassword();
            if (password.length() >= 8) {
                boolean hasLetter = password.matches(".*[A-Za-z].*");
                boolean hasDigit = password.matches(".*\\d.*");
                
                if (!hasLetter || !hasDigit) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "INVALID_PASSWORD", "message", "8자 이상인 경우 영문+숫자 조합이어야 합니다"));
                }
            }
            // 6. 휴대폰 번호 형식
            if (!PHONE_PATTERN.matcher(req.getPhoneNumber()).matches()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "INVALID_PHONE", "message", "휴대폰 번호 형식이 올바르지 않습니다"));
            }
            // 7. 비밀번호 암호화 (BCrypt 자동 salt 포함)
            String encoded = passwordEncoder.encode(req.getPassword());
            
            // 8. 가상계좌번호 생성
            String accountNumber = generateAccountNumber();
            
            // 9. User 저장 (핸드폰 번호에서 하이픈 제거)
            User user = new User();
            user.setPhoneNumber(req.getPhoneNumber().replace("-", ""));
            user.setPassword(encoded);
            user.setName(req.getName());
            user.setEmail(req.getEmail());
            user.setAccountNumber(accountNumber);
            User savedUser = userRepository.save(user);
            // 10. Account 생성 (PaymentService 호환성을 위해)
            Account account = Account.builder()
                    .accountNumber(accountNumber)
                    .userId(savedUser.getId())
                    .balance(BigDecimal.ZERO)
                    .status(AccountStatus.ACTIVE)
                    .build();
            accountRepository.save(account);
            // 11. AccountBalance 생성 (기존 호환성을 위해)
            AccountBalance accountBalance = AccountBalance.builder()
                    .accountNumber(accountNumber)
                    .balance(BigDecimal.ZERO)
                    .build();
            accountBalanceRepository.save(accountBalance);
            // 12. 토큰 쌍 생성 (Access Token + Refresh Token)
            TokenService.TokenPair tokenPair = tokenService.generateTokenPair(user);
            // 13. 감사로그 기록
            auditLogService.logSuccess(user.getId(), "USER_REGISTER", "USER", user.getId().toString(), 
                    null, "회원가입 완료 - 계좌번호: " + accountNumber);
            // 14. 응답
            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "회원가입이 완료되었습니다");
            resp.put("accessToken", tokenPair.getAccessToken());
            resp.put("refreshToken", tokenPair.getRefreshToken());
            resp.put("accountNumber", accountNumber);
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (Exception e) {
            auditLogService.logError(null, "USER_REGISTER", "USER", req.getPhoneNumber(), "회원가입 실패: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "회원가입 중 오류가 발생했습니다"));
        }
    }

    @Transactional
    public ResponseEntity<?> login(LoginRequest req) {
        String ipAddress = "127.0.0.1"; // 실제로는 HttpServletRequest에서 추출
        String userAgent = "Browser"; // 실제로는 HttpServletRequest에서 추출
        
        try {
            // 로그인 시에도 하이픈 제거
            String phoneNumber = req.getPhoneNumber().replace("-", "");
            Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
            if (userOpt.isEmpty()) {
                // 계정 없음 이력 기록
                loginHistoryService.recordLoginFailure(phoneNumber, null, "존재하지 않는 계정", ipAddress, userAgent, 0, false);
                auditLogService.logWarning(null, "LOGIN_ATTEMPT", "USER", phoneNumber, "존재하지 않는 계정으로 로그인 시도");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "INVALID_CREDENTIALS", "message", "휴대폰 번호 또는 비밀번호가 올바르지 않습니다"));
            }
            
            User user = userOpt.get();
            
            // 계정 잠금 확인
            if (user.isAccountLocked()) {
                loginHistoryService.recordAccountLocked(phoneNumber, user.getId(), user.getLockReason(), ipAddress, userAgent);
                auditLogService.logWarning(user.getId(), "LOGIN_BLOCKED", "USER", user.getId().toString(), "계정 잠금으로 로그인 차단");
                
                // 계정 잠금 알림 발송
                alarmService.sendAccountLockAlert(req.getPhoneNumber(), user.getId().toString(), user.getLockReason());
                
                return ResponseEntity.status(HttpStatus.LOCKED)
                        .body(Map.of("error", "ACCOUNT_LOCKED", "message", "계정이 잠겨있습니다"));
            }
            
            // 비밀번호 검증 (BCrypt 자동 salt 검증)
            if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
                // 로그인 실패 처리
                user.incrementLoginFailCount();
                userRepository.save(user);
                
                loginHistoryService.recordLoginFailure(phoneNumber, user.getId(), "잘못된 비밀번호", 
                        ipAddress, userAgent, user.getLoginFailCount(), user.isAccountLocked());
                
                auditLogService.logWarning(user.getId(), "LOGIN_FAIL", "USER", user.getId().toString(), 
                        "로그인 실패 - 실패 횟수: " + user.getLoginFailCount());
                
                // 로그인 실패 알림 발송
                alarmService.sendLoginFailureAlert(phoneNumber, user.getId().toString(), "잘못된 비밀번호");
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "INVALID_CREDENTIALS", "message", "휴대폰 번호 또는 비밀번호가 올바르지 않습니다"));
            }
            
            // 로그인 성공
            user.resetLoginFailCount();
            userRepository.save(user);
            
            // 토큰 쌍 생성 (Access Token + Refresh Token)
            TokenService.TokenPair tokenPair = tokenService.generateTokenPair(user);
            
            // 로그인 성공 이력 기록
            loginHistoryService.recordLoginSuccess(phoneNumber, user.getId(), ipAddress, userAgent);
            auditLogService.logSuccess(user.getId(), "USER_LOGIN", "USER", user.getId().toString(), null, "로그인 성공");
            
            // 로그인 성공 알림 생성
            String loginTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분"));
            alarmService.sendBusinessEvent("LOGIN_SUCCESS", user.getId().toString(), 
                String.format("로그인 성공 - %s에 로그인되었습니다", loginTime));
            
            Map<String, Object> resp = new HashMap<>();
            resp.put("accessToken", tokenPair.getAccessToken());
            resp.put("refreshToken", tokenPair.getRefreshToken());
            resp.put("accountNumber", user.getAccountNumber());
            resp.put("userName", user.getName());
            return ResponseEntity.ok(resp);
            
        } catch (Exception e) {
            auditLogService.logError(null, "USER_LOGIN", "USER", req.getPhoneNumber(), "로그인 처리 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "로그인 중 오류가 발생했습니다"));
        }
    }

    private String generateAccountNumber() {
        // 예시: VA + 8자리 랜덤 + 2자리 체크섬
        // 클래스 필드의 SECURE_RANDOM을 재사용
        String num = String.valueOf(SECURE_RANDOM.nextLong(10_000_000L, 99_999_999L));
        String base = "VA" + num;
        String checksum = String.valueOf((base.hashCode() & 0xFF));
        return base + checksum;
    }

    @Transactional
    public ResponseEntity<?> updateProfile(UserUpdateRequest req) {
        try {
            log.info("=== 개인정보 수정 시작 ===");
            
            // SecurityContext에서 현재 인증된 사용자 정보 가져오기
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
            }
            
            String phoneNumber = authentication.getName();
            Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "USER_NOT_FOUND", "message", "사용자를 찾을 수 없습니다"));
            }
            
            User user = userOpt.get();
            log.info("사용자 정보 - ID: {}, 이름: {}, 전화번호: {}", user.getId(), user.getName(), user.getPhoneNumber());
            
            // 이름 중복 검사 (다른 사용자가 사용 중인지)
            if (req.getName() != null && !req.getName().trim().isEmpty()) {
                Optional<User> existingUser = userRepository.findByName(req.getName());
                if (existingUser.isPresent() && !existingUser.get().getId().equals(user.getId())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "DUPLICATE_NAME", "message", "이미 사용 중인 이름입니다"));
                }
            }
            
            // 이메일 중복 검사 (다른 사용자가 사용 중인지)
            if (req.getEmail() != null && !req.getEmail().trim().isEmpty()) {
                if (!EMAIL_PATTERN.matcher(req.getEmail()).matches()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "INVALID_EMAIL", "message", "이메일 형식이 올바르지 않습니다"));
                }
                
                Optional<User> existingUser = userRepository.findByEmail(req.getEmail());
                if (existingUser.isPresent() && !existingUser.get().getId().equals(user.getId())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "DUPLICATE_EMAIL", "message", "이미 사용 중인 이메일입니다"));
                }
            }
            
            // 휴대폰 번호 중복 검사 (다른 사용자가 사용 중인지)
            if (req.getPhoneNumber() != null && !req.getPhoneNumber().trim().isEmpty()) {
                if (!PHONE_PATTERN.matcher(req.getPhoneNumber()).matches()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "INVALID_PHONE", "message", "휴대폰 번호 형식이 올바르지 않습니다"));
                }
                
                String phoneWithoutHyphen = req.getPhoneNumber().replace("-", "");
                Optional<User> existingUser = userRepository.findByPhoneNumber(phoneWithoutHyphen);
                if (existingUser.isPresent() && !existingUser.get().getId().equals(user.getId())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "DUPLICATE_PHONE", "message", "이미 사용 중인 휴대폰 번호입니다"));
                }
            }
            
            // 비밀번호 변경 처리
            if (req.getNewPassword() != null && !req.getNewPassword().trim().isEmpty()) {
                if (req.getNewPassword().length() < 8) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "INVALID_PASSWORD", "message", "비밀번호는 8자 이상이어야 합니다"));
                }
                
                String encodedPassword = passwordEncoder.encode(req.getNewPassword());
                user.setPassword(encodedPassword);
                log.info("비밀번호 변경 완료");
            }
            
            // 정보 업데이트
            if (req.getName() != null && !req.getName().trim().isEmpty()) {
                user.setName(req.getName());
            }
            
            if (req.getEmail() != null && !req.getEmail().trim().isEmpty()) {
                user.setEmail(req.getEmail());
            }
            
            if (req.getPhoneNumber() != null && !req.getPhoneNumber().trim().isEmpty()) {
                user.setPhoneNumber(req.getPhoneNumber().replace("-", ""));
            }
            
            userRepository.save(user);
            
            // 감사로그 기록
            auditLogService.logSuccess(user.getId(), "PROFILE_UPDATE", "USER", user.getId().toString(), 
                    null, "개인정보 수정 완료");
            
            return ResponseEntity.ok(Map.of("message", "개인정보가 성공적으로 수정되었습니다"));
            
        } catch (Exception e) {
            auditLogService.logError(null, "PROFILE_UPDATE", "USER", "unknown", "개인정보 수정 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "개인정보 수정 중 오류가 발생했습니다"));
        }
    }

    public ResponseEntity<?> checkPinRequired() {
        try {
            log.info("=== PIN 등록 필요 여부 확인 시작 ===");
            
            // SecurityContext에서 현재 인증된 사용자 정보 가져오기
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            log.info("인증 객체: {}", authentication);
            
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("인증되지 않은 사용자입니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
            }
            
            String phoneNumber = authentication.getName(); // JWT에서 추출된 전화번호
            log.info("인증된 사용자 전화번호: {}", phoneNumber);
            
            Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
            if (userOpt.isEmpty()) {
                log.warn("사용자를 찾을 수 없습니다. 전화번호: {}", phoneNumber);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "USER_NOT_FOUND", "message", "사용자를 찾을 수 없습니다"));
            }
            
            User user = userOpt.get();
            log.info("사용자 정보 - ID: {}, 이름: {}, 전화번호: {}, PIN: {}", 
                    user.getId(), user.getName(), user.getPhoneNumber(), user.getTransferPin());
            
            // PIN이 설정되어 있지 않은 경우
            boolean pinRequired = (user.getTransferPin() == null || user.getTransferPin().isEmpty());
            log.info("PIN 등록 필요 여부: {}", pinRequired);
            
            if (pinRequired) {
                log.info("PIN 등록이 필요합니다. pinRequired=true 반환");
                return ResponseEntity.ok(Map.of("pinRequired", true));
            } else {
                log.info("PIN이 이미 설정되어 있습니다. pinRequired=false 반환");
                return ResponseEntity.ok(Map.of("pinRequired", false));
            }
            
        } catch (Exception e) {
            log.error("PIN 등록 필요 여부 확인 중 오류 발생", e);
            auditLogService.logError(null, "PIN_CHECK", "USER", "unknown", "PIN 등록 필요 여부 확인 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "PIN 확인 중 오류가 발생했습니다"));
        }
    }

    public ResponseEntity<?> getProfile() {
        try {
            log.info("=== 프로필 조회 시작 ===");
            
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
            }
            
            String phoneNumber = authentication.getName();
            Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "USER_NOT_FOUND", "message", "사용자를 찾을 수 없습니다"));
            }
            
            User user = userOpt.get();
            Map<String, Object> profileData = new HashMap<>();
            profileData.put("success", true);
            profileData.put("name", user.getName());
            profileData.put("email", user.getEmail());
            profileData.put("phoneNumber", user.getPhoneNumber());
            
            log.info("프로필 조회 성공: {}", profileData);
            return ResponseEntity.ok(profileData);
            
        } catch (Exception e) {
            log.error("프로필 조회 중 오류 발생", e);
            auditLogService.logError(null, "PROFILE_GET", "USER", "unknown", "프로필 조회 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "프로필 조회 중 오류가 발생했습니다"));
        }
    }

    public ResponseEntity<?> verifyPassword(PasswordVerifyRequest req) {
        try {
            log.info("=== 비밀번호 확인 시작 ===");
            
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "인증이 필요합니다"));
            }
            
            String phoneNumber = authentication.getName();
            Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "USER_NOT_FOUND", "message", "사용자를 찾을 수 없습니다"));
            }
            
            User user = userOpt.get();
            
            // 비밀번호 확인 (BCrypt 자동 salt 검증)
            if (passwordEncoder.matches(req.getPassword(), user.getPassword())) {
                log.info("비밀번호 확인 성공");
                return ResponseEntity.ok(Map.of("success", true, "message", "비밀번호가 확인되었습니다"));
            } else {
                log.info("비밀번호 확인 실패");
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "비밀번호가 일치하지 않습니다"));
            }
            
        } catch (Exception e) {
            log.error("비밀번호 확인 중 오류 발생", e);
            auditLogService.logError(null, "PASSWORD_VERIFY", "USER", "unknown", "비밀번호 확인 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "비밀번호 확인 중 오류가 발생했습니다"));
        }
    }
} 