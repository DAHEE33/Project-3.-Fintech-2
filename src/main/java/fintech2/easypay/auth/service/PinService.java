package fintech2.easypay.auth.service;

import fintech2.easypay.auth.dto.*;
import fintech2.easypay.auth.entity.User;
import fintech2.easypay.auth.repository.UserRepository;
import fintech2.easypay.audit.service.AuditLogService;
import fintech2.easypay.audit.service.AlarmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PinService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final AlarmService alarmService;
    private final JwtService jwtService;
    
    // PIN 임시 세션 토큰의 만료 시간 (분)
    private static final int PIN_SESSION_EXPIRE_MINUTES = 5;
    
    /**
     * PIN 검증 (단순 boolean 반환)
     */
    @Transactional(readOnly = true)
    public boolean verifyPin(Long userId, String pin) {
        try {
            if (pin == null || pin.length() != 6) {
                return false;
            }
            
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return false;
            }
            
            User user = userOpt.get();
            if (!user.hasPinSet()) {
                return false;
            }
            
            return passwordEncoder.matches(pin, user.getTransferPin());
            
        } catch (Exception e) {
            log.error("PIN 검증 중 오류 발생: userId={}", userId, e);
            return false;
        }
    }

    /**
     * PIN 등록 (최초 설정)
     */
    @Transactional
    public PinResponse registerPin(String phoneNumber, PinRequest request) {
        try {
            // 입력 유효성 검사
            if (!request.isValidPin()) {
                auditLogService.logWarning(null, "PIN_REGISTER_FAIL", "USER", phoneNumber, "잘못된 PIN 형식");
                return PinResponse.failure("PIN은 6자리 숫자여야 합니다.");
            }

            // 사용자 조회
            Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
            if (userOpt.isEmpty()) {
                auditLogService.logWarning(null, "PIN_REGISTER_FAIL", "USER", phoneNumber, "존재하지 않는 사용자");
                return PinResponse.failure("사용자를 찾을 수 없습니다.");
            }

            User user = userOpt.get();

            // 현재 비밀번호 확인 (보안 강화)
            if (request.getCurrentPassword() == null || 
                !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                auditLogService.logWarning(user.getId(), "PIN_REGISTER_FAIL", "USER", user.getId().toString(), "현재 비밀번호 불일치");
                return PinResponse.failure("현재 비밀번호가 일치하지 않습니다.");
            }

            // 이미 PIN이 설정되어 있는지 확인
            if (user.hasPinSet()) {
                auditLogService.logWarning(user.getId(), "PIN_REGISTER_FAIL", "USER", user.getId().toString(), "이미 PIN이 설정됨");
                return PinResponse.failure("이미 PIN이 설정되어 있습니다. PIN 변경을 이용해주세요.");
            }

            // PIN 암호화 및 저장
            String encodedPin = passwordEncoder.encode(request.getPin());
            user.setTransferPin(encodedPin);
            user.setPinCreatedAt(LocalDateTime.now());
            user.resetPinFailCount(); // 초기화
            
            userRepository.save(user);

            // 감사 로그 및 알림
            auditLogService.logSuccess(user.getId(), "PIN_REGISTER", "USER", user.getId().toString(), 
                    null, "PIN 등록 완료");
            alarmService.sendBusinessEvent("PIN_CREATED", user.getId().toString(), 
                    "보안 PIN이 성공적으로 등록되었습니다.");

            log.info("PIN 등록 성공: userId={}", user.getId());
            return PinResponse.success("PIN이 성공적으로 등록되었습니다.");

        } catch (Exception e) {
            log.error("PIN 등록 중 오류 발생: phoneNumber={}", phoneNumber, e);
            auditLogService.logError(null, "PIN_REGISTER", "USER", phoneNumber, "PIN 등록 오류: " + e.getMessage());
            return PinResponse.failure("PIN 등록 중 오류가 발생했습니다.");
        }
    }

    /**
     * PIN 검증
     */
    @Transactional
    public PinResponse verifyPin(String phoneNumber, PinVerifyRequest request) {
        try {
            // 입력 유효성 검사
            if (!request.isValidPin()) {
                auditLogService.logWarning(null, "PIN_VERIFY_FAIL", "USER", phoneNumber, "잘못된 PIN 형식");
                return PinResponse.failure("PIN은 6자리 숫자여야 합니다.");
            }

            if (!request.isValidPurpose()) {
                auditLogService.logWarning(null, "PIN_VERIFY_FAIL", "USER", phoneNumber, "잘못된 목적");
                return PinResponse.failure("잘못된 요청입니다.");
            }

            // 사용자 조회
            Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
            if (userOpt.isEmpty()) {
                auditLogService.logWarning(null, "PIN_VERIFY_FAIL", "USER", phoneNumber, "존재하지 않는 사용자");
                return PinResponse.failure("사용자를 찾을 수 없습니다.");
            }

            User user = userOpt.get();

            // PIN 설정 여부 확인
            if (!user.hasPinSet()) {
                auditLogService.logWarning(user.getId(), "PIN_VERIFY_FAIL", "USER", user.getId().toString(), "PIN 미설정");
                return PinResponse.failure("PIN이 설정되지 않았습니다. 먼저 PIN을 등록해주세요.");
            }

            // PIN 잠금 상태 확인
            if (user.isPinLocked()) {
                auditLogService.logWarning(user.getId(), "PIN_VERIFY_BLOCKED", "USER", user.getId().toString(), 
                        "PIN 잠금으로 검증 차단 - " + user.getPinLockReason());
                alarmService.sendBusinessEvent("PIN_LOCKED", user.getId().toString(), 
                        "PIN이 잠금되어 거래가 차단되었습니다.");
                return PinResponse.locked(user.getPinLockReason());
            }

            // PIN 검증
            if (!passwordEncoder.matches(request.getPin(), user.getTransferPin())) {
                // PIN 실패 처리
                user.incrementPinFailCount();
                userRepository.save(user);

                int remainingAttempts = Math.max(0, 5 - user.getPinFailCount());
                
                auditLogService.logWarning(user.getId(), "PIN_VERIFY_FAIL", "USER", user.getId().toString(), 
                        String.format("PIN 검증 실패 - 실패 횟수: %d, 목적: %s", user.getPinFailCount(), request.getPurpose()));

                // PIN 잠금 알림
                if (user.isPinLocked()) {
                    alarmService.sendBusinessEvent("PIN_LOCKED_FAILURE", user.getId().toString(), 
                            "PIN 5회 실패로 인해 PIN이 잠금되었습니다.");
                    return PinResponse.locked("PIN 5회 실패로 인한 PIN 잠금");
                } else {
                    alarmService.sendBusinessEvent("PIN_VERIFY_FAIL", user.getId().toString(), 
                            String.format("PIN 검증 실패 (남은 시도: %d회)", remainingAttempts));
                }

                return PinResponse.failure("PIN이 일치하지 않습니다.", remainingAttempts);
            }

            // PIN 검증 성공
            user.resetPinFailCount();
            user.updatePinLastUsed();
            userRepository.save(user);

            // 임시 세션 토큰 생성 (PIN 인증 후 짧은 시간 동안 유효)
            String sessionToken = generatePinSessionToken(user.getId(), request.getPurpose());

            auditLogService.logSuccess(user.getId(), "PIN_VERIFY_SUCCESS", "USER", user.getId().toString(), 
                    null, String.format("PIN 검증 성공 - 목적: %s", request.getPurpose()));

            log.info("PIN 검증 성공: userId={}, purpose={}", user.getId(), request.getPurpose());
            return PinResponse.success("PIN 인증이 완료되었습니다.", sessionToken);

        } catch (Exception e) {
            log.error("PIN 검증 중 오류 발생: phoneNumber={}", phoneNumber, e);
            auditLogService.logError(null, "PIN_VERIFY", "USER", phoneNumber, "PIN 검증 오류: " + e.getMessage());
            return PinResponse.failure("PIN 검증 중 오류가 발생했습니다.");
        }
    }

    /**
     * PIN 변경
     */
    @Transactional
    public PinResponse changePin(String phoneNumber, ChangePinRequest request) {
        try {
            // 입력 유효성 검사
            if (!request.isValidCurrentPin() || !request.isValidNewPin()) {
                auditLogService.logWarning(null, "PIN_CHANGE_FAIL", "USER", phoneNumber, "잘못된 PIN 형식");
                return PinResponse.failure("PIN은 6자리 숫자여야 합니다.");
            }

            if (!request.arePinsDifferent()) {
                auditLogService.logWarning(null, "PIN_CHANGE_FAIL", "USER", phoneNumber, "동일한 PIN으로 변경 시도");
                return PinResponse.failure("새로운 PIN은 기존 PIN과 달라야 합니다.");
            }

            // 사용자 조회
            Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
            if (userOpt.isEmpty()) {
                auditLogService.logWarning(null, "PIN_CHANGE_FAIL", "USER", phoneNumber, "존재하지 않는 사용자");
                return PinResponse.failure("사용자를 찾을 수 없습니다.");
            }

            User user = userOpt.get();

            // PIN 설정 여부 확인
            if (!user.hasPinSet()) {
                auditLogService.logWarning(user.getId(), "PIN_CHANGE_FAIL", "USER", user.getId().toString(), "PIN 미설정");
                return PinResponse.failure("PIN이 설정되지 않았습니다. 먼저 PIN을 등록해주세요.");
            }

            // PIN 잠금 상태 확인
            if (user.isPinLocked()) {
                auditLogService.logWarning(user.getId(), "PIN_CHANGE_BLOCKED", "USER", user.getId().toString(), 
                        "PIN 잠금으로 변경 차단");
                return PinResponse.locked(user.getPinLockReason());
            }

            // 현재 PIN 검증
            if (!passwordEncoder.matches(request.getCurrentPin(), user.getTransferPin())) {
                user.incrementPinFailCount();
                userRepository.save(user);

                auditLogService.logWarning(user.getId(), "PIN_CHANGE_FAIL", "USER", user.getId().toString(), 
                        "현재 PIN 불일치");

                if (user.isPinLocked()) {
                    alarmService.sendBusinessEvent("PIN_LOCKED_CHANGE", user.getId().toString(), 
                            "PIN 변경 시도 실패로 인해 PIN이 잠금되었습니다.");
                    return PinResponse.locked("PIN 5회 실패로 인한 PIN 잠금");
                }

                return PinResponse.failure("현재 PIN이 일치하지 않습니다.", 
                        Math.max(0, 5 - user.getPinFailCount()));
            }

            // PIN 변경
            String encodedNewPin = passwordEncoder.encode(request.getNewPin());
            user.setTransferPin(encodedNewPin);
            user.resetPinFailCount();
            user.updatePinLastUsed();
            
            userRepository.save(user);

            // 감사 로그 및 알림
            auditLogService.logSuccess(user.getId(), "PIN_CHANGE", "USER", user.getId().toString(), 
                    null, "PIN 변경 완료");
            alarmService.sendBusinessEvent("PIN_CHANGED", user.getId().toString(), 
                    "보안 PIN이 성공적으로 변경되었습니다.");

            log.info("PIN 변경 성공: userId={}", user.getId());
            return PinResponse.success("PIN이 성공적으로 변경되었습니다.");

        } catch (Exception e) {
            log.error("PIN 변경 중 오류 발생: phoneNumber={}", phoneNumber, e);
            auditLogService.logError(null, "PIN_CHANGE", "USER", phoneNumber, "PIN 변경 오류: " + e.getMessage());
            return PinResponse.failure("PIN 변경 중 오류가 발생했습니다.");
        }
    }

    /**
     * PIN 상태 조회
     */
    public PinResponse getPinStatus(String phoneNumber) {
        try {
            Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
            if (userOpt.isEmpty()) {
                return PinResponse.failure("사용자를 찾을 수 없습니다.");
            }

            User user = userOpt.get();
            
            return PinResponse.builder()
                    .success(true)
                    .message("PIN 상태 조회 성공")
                    .hasPinSet(user.hasPinSet())
                    .isPinLocked(user.isPinLocked())
                    .remainingAttempts(user.isPinLocked() ? 0 : Math.max(0, 5 - user.getPinFailCount()))
                    .lockReason(user.getPinLockReason())
                    .build();

        } catch (Exception e) {
            log.error("PIN 상태 조회 중 오류 발생: phoneNumber={}", phoneNumber, e);
            return PinResponse.failure("PIN 상태 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * PIN 세션 토큰 검증
     */
    public boolean validatePinSessionToken(String sessionToken, String purpose) {
        try {
            return jwtService.validatePinSessionToken(sessionToken, purpose);
        } catch (Exception e) {
            log.error("PIN 세션 토큰 검증 중 오류 발생: token={}", sessionToken, e);
            return false;
        }
    }

    /**
     * PIN 인증 후 임시 세션 토큰 생성
     */
    private String generatePinSessionToken(Long userId, String purpose) {
        // PIN 인증 후 짧은 시간(5분) 동안 유효한 세션 토큰 생성
        return jwtService.generatePinSessionToken(userId, purpose, PIN_SESSION_EXPIRE_MINUTES);
    }
}