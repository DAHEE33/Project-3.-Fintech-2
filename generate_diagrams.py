import requests
import base64
import json
import os

def generate_mermaid_image(mermaid_code, filename, format="png"):
    """
    Mermaid 코드를 이미지로 변환하여 저장
    """
    # Mermaid Live Editor API 사용
    url = "https://mermaid.ink/img/"
    
    # Mermaid 코드를 base64로 인코딩
    encoded_code = base64.b64encode(mermaid_code.encode()).decode()
    
    # 이미지 URL 생성
    image_url = f"{url}{encoded_code}.{format}"
    
    try:
        # 이미지 다운로드
        response = requests.get(image_url)
        response.raise_for_status()
        
        # 파일로 저장
        with open(f"{filename}.{format}", "wb") as f:
            f.write(response.content)
        
        print(f"✅ {filename}.{format} 저장 완료!")
        return True
    except Exception as e:
        print(f"❌ 오류: {e}")
        return False

# 전체 아키텍처 다이어그램
architecture_diagram = """
graph LR
  User["Client (Browser<br/>static HTML/JS)"] -->|HTTPS| Nginx["Nginx (reverse proxy)"]
  Nginx --> App["Spring Boot App (Easypay)"]
  
  subgraph AppSub["Easypay Spring Boot"]
    direction TB
    Sec["SecurityConfig<br/>JwtAuthenticationFilter"]
    Ctrls["Controllers<br/>- auth<br/>- account<br/>- payment<br/>- transfer<br/>- audit"]
    Svcs["Services<br/>- domain services<br/>- Token/Pin/LoginHistory<br/>- Audit/Notification<br/>- Scheduling"]
    Repos["Repositories (JPA)"]
    Ext["External APIs<br/>- BankingApiService (transfer)<br/>- ExternalBankApiService (account verify)<br/>- PaymentGatewayService (payment)"]
    Audit["AuditLogService"]
  end
  
  Nginx --> Sec
  Sec --> Ctrls
  Ctrls --> Svcs
  Svcs --> Repos
  Svcs --> Ext
  Svcs --> Audit
  
  Repos --> DB[(Database)]
"""

# 인증 흐름 다이어그램
auth_flow_diagram = """
sequenceDiagram
  autonumber
  participant UI as UI
  participant Nginx as Nginx
  participant Filter as JwtAuthenticationFilter
  participant C as AuthController
  participant S as AuthService
  participant URepo as UserRepository
  participant J as JwtService
  participant RRepo as RefreshTokenRepository
  participant L as LoginHistoryService
  participant A as AuditLogService

  UI->>Nginx: POST /api/auth/login
  Nginx->>C: Forward (login endpoint)
  C->>S: login(LoginRequest)
  S->>URepo: findByUsername()
  S-->>C: if invalid -> AuthException
  S->>J: generateAccessToken()
  S->>RRepo: saveRefreshToken()
  S->>L: recordLogin(SUCCESS/FAIL)
  S->>A: log(Login)
  C-->>UI: 200 AuthResponse(tokens)

  Note over UI,Filter: Protected API call
  UI->>Nginx: GET /api/account/balance (Bearer JWT)
  Nginx->>Filter: Intercept
  Filter->>J: validateToken()
  Filter-->>C: Set Authentication or 401
"""

# 계정 흐름 다이어그램
account_flow_diagram = """
sequenceDiagram
  autonumber
  participant UI as UI
  participant C as AccountController
  participant S as AccountService/BalanceService
  participant ARepo as AccountRepository
  participant BRepo as AccountBalanceRepository
  participant E as ExternalBankApiService
  participant ALog as AuditLogService

  UI->>C: GET /api/account/balance
  C->>S: getBalance(userId)
  S->>BRepo: findByUser()
  S-->>C: BalanceResponse
  C-->>UI: 200

  UI->>C: POST /api/account/verify
  C->>S: verifyAccount(request)
  S->>E: call external verify API
  E-->>S: verification result
  S->>ALog: log(AccountVerification)
  S-->>C: response
  C-->>UI: 200
"""

# 결제 흐름 다이어그램
payment_flow_diagram = """
sequenceDiagram
  autonumber
  participant UI as UI
  participant C as PaymentController
  participant S as PaymentService
  participant M as PaymentMemberService
  participant PA as PaymentAccountService
  participant G as PaymentGatewayService
  participant PR as PaymentRepository
  participant AU as PaymentAuditService
  participant N as PaymentNotificationService

  UI->>C: POST /api/payment
  C->>S: pay(PaymentRequest)
  S->>M: validateMember()
  S->>PA: reserveAmount()
  S->>G: requestPayment()
  G-->>S: PgApiResponse(status)
  alt success
    S->>PR: save(Payment SUCCESS)
    S->>AU: recordSuccess()
    S->>N: notifySuccess()
  else failure
    S->>PA: rollbackReservation()
    S->>PR: save(Payment FAILED)
    S->>AU: recordFailure()
    S->>N: notifyFailure()
  end
  S-->>C: PaymentResponse
  C-->>UI: 200/4xx
"""

# 이체 흐름 다이어그램
transfer_flow_diagram = """
sequenceDiagram
  autonumber
  participant UI as UI
  participant C as TransferController
  participant S as TransferService
  participant PR as TransferRepository
  participant B as BankingApiService
  participant ST as TransferStatusCheckService

  UI->>C: POST /api/transfer
  C->>S: createTransfer(request)
  S->>PR: save(PENDING)
  S->>B: requestTransfer()
  B-->>S: BankingApiResponse(status)
  alt immediate success
    S->>PR: update(SUCCESS)
  else pending
    S->>PR: update(PENDING)
    ST->>B: scheduled status check
    B-->>ST: status update
    ST->>PR: update status
  end
  S-->>C: TransferResponse
  C-->>UI: 200
"""

# 감사/알람 흐름 다이어그램
audit_flow_diagram = """
sequenceDiagram
  autonumber
  participant UI as UI
  participant C as AlarmController
  participant S as AlarmService
  participant N as NotificationService
  participant AR as AuditLogRepository

  UI->>C: POST /api/audit/alarm
  C->>S: sendAlarm(event)
  S->>N: notify(event)
  S->>AR: save(AuditLog)
  C-->>UI: 200
"""

if __name__ == "__main__":
    print("🎨 Mermaid 다이어그램을 이미지로 변환 중...")
    
    # 다이어그램들 생성
    diagrams = [
        ("01_전체_아키텍처", architecture_diagram),
        ("02_인증_흐름", auth_flow_diagram),
        ("03_계정_흐름", account_flow_diagram),
        ("04_결제_흐름", payment_flow_diagram),
        ("05_이체_흐름", transfer_flow_diagram),
        ("06_감사_알람_흐름", audit_flow_diagram)
    ]
    
    for name, diagram in diagrams:
        generate_mermaid_image(diagram, name)
    
    print("\n🎉 모든 다이어그램 생성 완료!")
    print("📁 현재 폴더에서 PNG 파일들을 확인하세요.")
