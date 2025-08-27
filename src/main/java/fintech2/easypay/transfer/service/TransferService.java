package fintech2.easypay.transfer.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.account.repository.AccountRepository;
import fintech2.easypay.account.service.BalanceService;
import fintech2.easypay.common.enums.AuditEventType;
import fintech2.easypay.common.enums.TransactionType;
import fintech2.easypay.audit.service.AuditLogService;
import fintech2.easypay.audit.service.NotificationService;
import fintech2.easypay.common.BusinessException;
import fintech2.easypay.common.ErrorCode;
import fintech2.easypay.auth.entity.User;
import fintech2.easypay.auth.repository.UserRepository;
import fintech2.easypay.transfer.dto.RecentTransferResponse;
import fintech2.easypay.transfer.dto.TransferRequest;
import fintech2.easypay.transfer.dto.TransferResponse;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.entity.TransferStatus;
import fintech2.easypay.transfer.external.BankingApiRequest;
import fintech2.easypay.transfer.external.BankingApiResponse;
import fintech2.easypay.transfer.external.BankingApiService;
import fintech2.easypay.transfer.external.BankingApiStatus;
import fintech2.easypay.transfer.repository.TransferRepository;
import org.springframework.context.ApplicationContext;

/**
 * 송금 서비스
 * 사용자 간 송금 처리 및 거래 내역 관리
 * 동시성 제어와 감사 로그를 포함한 안전한 송금 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TransferService {
    
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final BalanceService balanceService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final BankingApiService bankingApiService;
    private final ApplicationContext applicationContext;
    
    /**
     * 사용자 간 송금 처리
     * 송금자와 수신자 계좌에 대한 배타적 락을 사용하여 동시성 제어
     * 거래 실패 시 롤백 및 감사 로그 기록
     * @param senderPhoneNumber 송금자 휴대폰 번호
     * @param request 송금 요청 정보
     * @return 송금 처리 결과
     * @throws BusinessException 송금 처리 중 오류 발생 시
     */
    @Transactional
    public TransferResponse transfer(String senderPhoneNumber, TransferRequest request) {
        // 송금자 조회
        User sender = userRepository.findByPhoneNumber(senderPhoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        // 수신자 계좌 조회
        Account receiverAccount = accountRepository
            .findByAccountNumber(request.getReceiverAccountNumber())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER));
        
        // Account엔티티에서 User를 직접 참조할 수 없으므로 userId로 조회
        User receiver = userRepository.findById(receiverAccount.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        // 자기 자신에게 송금 방지
        if (sender.getId().equals(receiver.getId())) {
            throw new BusinessException(ErrorCode.SAME_ACCOUNT_TRANSFER);
        }
        
        // 송금자 계좌 조회 (요청에 계좌번호가 있으면 해당 계좌 사용, 없으면 주계좌 사용)
        Account senderAccount;
        if (request.getSenderAccountNumber() != null && !request.getSenderAccountNumber().trim().isEmpty()) {
            // 특정 계좌 지정된 경우
            senderAccount = accountRepository.findByAccountNumber(request.getSenderAccountNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
            
            // 송금자 본인의 계좌인지 확인
            if (!senderAccount.getUserId().equals(sender.getId())) {
                throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "본인 계좌가 아닙니다.");
            }
        } else {
            // UserAccount에서 기본 계좌 사용 (수정된 로직)
            fintech2.easypay.account.service.UserAccountService userAccountService = 
                applicationContext.getBean(fintech2.easypay.account.service.UserAccountService.class);
            fintech2.easypay.account.entity.UserAccount primaryUserAccount = userAccountService.getPrimaryAccount(sender.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "기본 계좌를 찾을 수 없습니다."));
            
            // UserAccount의 계좌번호로 Account 엔티티 조회
            senderAccount = accountRepository.findByAccountNumber(primaryUserAccount.getAccountNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "기본 계좌에 해당하는 Account를 찾을 수 없습니다."));
        }
        
        // 데드락 방지를 위한 순서대로 락 획득
        Account senderAccountLocked;
        Account receiverAccountLocked;
        
        // 계좌 ID 순서로 락 획득 순서 결정 (데드락 방지)
        if (senderAccount.getId().compareTo(receiverAccount.getId()) < 0) {
            // 송금자 계좌 ID가 더 작은 경우
            senderAccountLocked = accountRepository.findByIdWithLock(senderAccount.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
            receiverAccountLocked = accountRepository.findByIdWithLock(receiverAccount.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER));
        } else {
            // 수신자 계좌 ID가 더 작은 경우
            receiverAccountLocked = accountRepository.findByIdWithLock(receiverAccount.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER));
            senderAccountLocked = accountRepository.findByIdWithLock(senderAccount.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        }
        
        // 잔액 충분성 검증 (BalanceService 사용)
        if (!balanceService.hasSufficientBalance(senderAccountLocked.getAccountNumber(), request.getAmount())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        
        // 거래 ID 생성
        String transactionId = generateTransactionId();
        
        // 송금 기록 생성
        Transfer transfer = Transfer.builder()
                .transactionId(transactionId)
                .sender(sender)
                .senderAccountNumber(senderAccountLocked.getAccountNumber())
                .receiver(receiver)
                .receiverAccountNumber(receiverAccountLocked.getAccountNumber())
                .amount(request.getAmount())
                .memo(request.getMemo())
                .build();
        
        try {
            // 1. 송금 요청을 PENDING 상태로 DB 저장
            Transfer savedTransfer = transferRepository.save(transfer);
            
            // 2. 송금 처리 중 상태로 변경
            transfer.markAsProcessing();
            transferRepository.save(transfer);
            
            // 3. 외부 뱅킹 API 호출
            BankingApiRequest apiRequest = BankingApiRequest.builder()
                    .transactionId(transactionId)
                    .senderAccountNumber(senderAccountLocked.getAccountNumber())
                    .senderBankCode("EASYPAY") // 실제로는 은행 코드 사용
                    .receiverAccountNumber(receiverAccountLocked.getAccountNumber())
                    .receiverBankCode("EASYPAY") // 실제로는 은행 코드 사용
                    .amount(request.getAmount())
                    .currency("KRW")
                    .memo(request.getMemo())
                    .build();
            
            log.info("외부 뱅킹 API 호출 시작: {}", transactionId);
            BankingApiResponse apiResponse = bankingApiService.processTransfer(apiRequest);
            
            // 4. API 응답에 따른 처리
            if (apiResponse.getStatus() == BankingApiStatus.SUCCESS) {
                // 4-1. 성공 시 잔액 이동 (BalanceService 사용)
                balanceService.decrease(senderAccountLocked.getAccountNumber(), request.getAmount(), 
                    TransactionType.TRANSFER_OUT, "송금 출금: " + request.getMemo(), transactionId, sender.getId().toString());
                balanceService.increase(receiverAccountLocked.getAccountNumber(), request.getAmount(), 
                    TransactionType.TRANSFER_IN, "송금 입금: " + request.getMemo(), transactionId, receiver.getId().toString());
                
                // 4-2. 송금 완료 상태로 변경
                transfer.markAsCompleted();
                transfer.setBankTransactionId(apiResponse.getBankTransactionId());
            } else if (apiResponse.getStatus() == BankingApiStatus.TIMEOUT) {
                // 4-3. 타임아웃 시 처리 - 잔액 이동하지 않고 TIMEOUT 상태로 설정
                String timeoutReason = String.format("외부 API 타임아웃: %s", 
                    apiResponse.getErrorMessage());
                transfer.markAsTimeout(timeoutReason);
                
                log.warn("송금 타임아웃 발생: {} - 별도 상태 확인 필요", transactionId);
                // 타임아웃 시에는 예외를 던지지 않고 사용자에게 상태 확인 안내
            } else if (apiResponse.getStatus() == BankingApiStatus.UNKNOWN) {
                // 4-4. 알 수 없음 상태 처리
                String unknownReason = "외부 API 응답 상태를 확인할 수 없습니다.";
                transfer.markAsUnknown(unknownReason);
                
                log.warn("송금 상태 불명: {} - 별도 상태 확인 필요", transactionId);
            } else if (apiResponse.getStatus() == BankingApiStatus.PENDING) {
                // 4-5. 처리중 상태 유지
                log.info("송금 처리 중: {} - 별도 스케줄러가 상태 업데이트 예정", transactionId);
            } else {
                // 4-6. 기타 실패 시 처리
                String failureReason = String.format("외부 API 오류: %s - %s", 
                    apiResponse.getStatus().getDescription(), 
                    apiResponse.getErrorMessage());
                transfer.markAsFailed(failureReason);
                
                throw new BusinessException(ErrorCode.TRANSACTION_FAILED, failureReason);
            }
            
            // 감사 로그 기록
            auditLogService.logSuccess(
                sender.getId(),
                senderPhoneNumber,
                AuditEventType.TRANSFER_SUCCESS,
                String.format("송금 완료: %s -> %s (%s원)", 
                    senderAccountLocked.getAccountNumber(), 
                    receiverAccountLocked.getAccountNumber(), 
                    request.getAmount()),
                null, null,
                String.format("amount: %s, memo: %s", request.getAmount(), request.getMemo()),
                String.format("transactionId: %s", transactionId)
            );
            
            // 알림 전송
            notificationService.sendTransferActivityNotification(
                sender.getId(),
                senderPhoneNumber,
                String.format("%s원이 %s로 송금되었습니다.", request.getAmount(), receiverAccountLocked.getAccountNumber())
            );
            
            notificationService.sendTransferActivityNotification(
                receiver.getId(),
                receiver.getPhoneNumber(),
                String.format("%s원이 %s로부터 입금되었습니다.", request.getAmount(), senderAccountLocked.getAccountNumber())
            );
            
            log.info("송금 완료: {} -> {} ({}원)", senderAccountLocked.getAccountNumber(), 
                    receiverAccountLocked.getAccountNumber(), request.getAmount());
            
            return TransferResponse.from(savedTransfer);
            
        } catch (Exception e) {
            // 송금 실패 처리
            transfer.markAsFailed(e.getMessage());
            
            // 감사 로그 기록
            auditLogService.logFailure(
                sender.getId(),
                senderPhoneNumber,
                AuditEventType.TRANSFER_FAILED,
                "송금 실패: " + e.getMessage(),
                null, null,
                String.format("amount: %s, memo: %s", request.getAmount(), request.getMemo()),
                e.getMessage()
            );
            
            log.error("송금 실패: {} -> {} ({}원) - {}", 
                    senderAccountLocked.getAccountNumber(), 
                    receiverAccountLocked.getAccountNumber(), 
                    request.getAmount(), e.getMessage());
            
            throw new BusinessException(ErrorCode.TRANSACTION_FAILED, e.getMessage());
        }
    }
    
    public TransferResponse getTransfer(String transactionId) {
        Transfer transfer = transferRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
        
        return TransferResponse.from(transfer);
    }
    
    public Page<TransferResponse> getTransferHistory(String phoneNumber, Pageable pageable) {
        Page<Transfer> transfers = transferRepository.findByPhoneNumberOrderByCreatedAtDesc(phoneNumber, pageable);
        return transfers.map(TransferResponse::from);
    }
    
    public Page<TransferResponse> getSentTransfers(String phoneNumber, Pageable pageable) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        Page<Transfer> transfers = transferRepository.findBySenderIdOrderByCreatedAtDesc(user.getId(), pageable);
        return transfers.map(TransferResponse::from);
    }
    
    public Page<TransferResponse> getReceivedTransfers(String phoneNumber, Pageable pageable) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        Page<Transfer> transfers = transferRepository.findByReceiverIdOrderByCreatedAtDesc(user.getId(), pageable);
        return transfers.map(TransferResponse::from);
    }
    
    /**
     * 최근 송금한 사람들의 목록 조회 (중복 제거)
     * 가장 최근에 송금한 순서대로 정렬
     * @param phoneNumber 사용자 휴대폰 번호
     * @param pageable 페이지 정보
     * @return 최근 송금 대상 목록
     */
    public Page<RecentTransferResponse> getRecentTransfers(String phoneNumber, Pageable pageable) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        // 성공한 송금만 조회하고, 수신자별로 가장 최근 거래만 가져옴
        Page<Transfer> transfers = transferRepository.findRecentDistinctReceivers(user.getId(), pageable);
        return transfers.map(RecentTransferResponse::from);
    }
    
    /**
     * 고유한 거래 ID 생성
     * TXN 접두어 + 12자리 랜덤 문자열
     * 중복 방지를 위한 검증 로직 포함
     * @return 생성된 거래 ID
     */
    private String generateTransactionId() {
        String transactionId;
        do {
            transactionId = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        } while (transferRepository.existsByTransactionId(transactionId));
        
        return transactionId;
    }
}
