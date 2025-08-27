// 토큰 갱신 중복 방지 플래그는 common.js에서 관리됨

// 페이지 로드 시 실행
document.addEventListener('DOMContentLoaded', async function() {
    // 현재 페이지가 로그인 관련 페이지인지 확인
    const isLoginPage = window.location.pathname.includes('login') || 
                       window.location.pathname.includes('register') || 
                       window.location.pathname === '/' || 
                       window.location.pathname === '/index.html';
    
    // 로그인 페이지가 아닐 때만 토큰 체크 실행
    if (!isLoginPage) {
        // 서버에서 클라이언트 설정 로드
        await loadClientConfig();
        
        // 서버 설정을 사용한 토큰 체크 시작
        setInterval(checkTokenExpiration, clientConfig.accessCheckInterval);
        setInterval(backgroundTokenCheck, clientConfig.backgroundCheckInterval);
        
        // 초기 토큰 체크는 즉시 실행
        checkTokenExpiration();
        backgroundTokenCheck();
    }
    
    checkAuth();
    loadBalance();
    loadTransactionHistory();
});

// 인증 확인
function checkAuth() {
    const token = localStorage.getItem('accessToken');
    if (!token) {
        alert('로그인이 필요합니다.');
        window.location.href = '/index.html';
        return;
    }
}

// 전역 변수 - 현재 기본 계좌 정보
let currentPrimaryAccount = null;

// 잔액 조회 (기본 계좌 사용)
async function loadBalance() {
    const token = localStorage.getItem('accessToken');
    
    if (!token) {
        showAlert('로그인 정보가 없습니다.');
        return;
    }

    try {
        const response = await fetch('/api/user-accounts', {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (response.ok) {
            const result = await response.json();
            const accounts = result.accounts || [];
            
            // 기본 계좌 찾기 (isPrimary가 true인 계좌)
            const primaryAccount = accounts.find(account => account.isPrimary);
            
            if (primaryAccount) {
                const balance = primaryAccount.balance || 0;
                const accountNumber = primaryAccount.accountNumber || 'N/A';
                const accountName = primaryAccount.accountName || '기본계좌';
                
                // 전역 변수에 현재 기본 계좌 저장
                currentPrimaryAccount = primaryAccount;
                
                // localStorage에도 업데이트 (거래내역 조회용)
                localStorage.setItem('accountNumber', accountNumber);
                
                document.getElementById('currentBalance').textContent = 
                    `${Number(balance).toLocaleString()} 원`;
                document.getElementById('balanceAccountNumber').textContent = 
                    `${accountName} (${accountNumber})`;
                
                console.log('기본 계좌 잔액 조회 성공:', { balance, accountNumber, accountName });
            } else if (accounts.length > 0) {
                // 기본 계좌가 없으면 첫 번째 계좌 사용
                const firstAccount = accounts[0];
                const balance = firstAccount.balance || 0;
                const accountNumber = firstAccount.accountNumber || 'N/A';
                const accountName = firstAccount.accountName || '계좌';
                
                currentPrimaryAccount = firstAccount;
                localStorage.setItem('accountNumber', accountNumber);
                
                document.getElementById('currentBalance').textContent = 
                    `${Number(balance).toLocaleString()} 원`;
                document.getElementById('balanceAccountNumber').textContent = 
                    `${accountName} (${accountNumber})`;
                    
                console.log('첫 번째 계좌 잔액 조회 성공:', { balance, accountNumber, accountName });
            } else {
                // 계좌가 없는 경우
                currentPrimaryAccount = null;
                localStorage.removeItem('accountNumber');
                
                document.getElementById('currentBalance').textContent = '0 원';
                document.getElementById('balanceAccountNumber').textContent = '계좌를 생성해주세요';
                
                console.log('등록된 계좌가 없습니다.');
            }
        } else {
            console.error('계좌 목록 조회 실패:', response.status);
            document.getElementById('currentBalance').textContent = '조회 실패';
            document.getElementById('balanceAccountNumber').textContent = '오류 발생';
        }
    } catch (error) {
        console.error('Balance load error:', error);
        showAlert('잔액 조회에 실패했습니다.');
    }
}

// 테스트 입금
async function testDeposit() {
    const amount = document.getElementById('testAmount').value;
    
    if (!amount || amount <= 0) {
        showAlert('올바른 금액을 입력해주세요.');
        return;
    }

    const token = localStorage.getItem('accessToken');
    const accountNumber = localStorage.getItem('accountNumber'); // 실제 계좌번호 사용

    try {
        const response = await fetch('/api/accounts/deposit', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
                amount: Number(amount),
                memo: '테스트 입금'
            })
        });

        if (response.ok) {
            showAlert('입금이 완료되었습니다!');
            loadBalance(); // 잔액 새로고침
            loadTransactionHistory(); // 거래내역 새로고침
            
            // 계좌관리 페이지를 위한 이벤트 발생
            window.dispatchEvent(new CustomEvent('accountBalanceUpdated'));
        } else {
            const data = await response.json();
            showAlert(data.message || '입금에 실패했습니다.');
        }
    } catch (error) {
        console.error('Deposit error:', error);
        showAlert('서버 연결에 실패했습니다.');
    }
}

// 테스트 출금
async function testWithdraw() {
    const amount = document.getElementById('testAmount').value;
    
    if (!amount || amount <= 0) {
        showAlert('올바른 금액을 입력해주세요.');
        return;
    }

    const token = localStorage.getItem('accessToken');
    const accountNumber = localStorage.getItem('accountNumber'); // 실제 계좌번호 사용

    try {
        const response = await fetch('/api/accounts/withdraw', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
                amount: Number(amount),
                memo: '테스트 출금'
            })
        });

        if (response.ok) {
            showAlert('출금이 완료되었습니다!');
            loadBalance(); // 잔액 새로고침
            loadTransactionHistory(); // 거래내역 새로고침
            
            // 계좌관리 페이지를 위한 이벤트 발생
            window.dispatchEvent(new CustomEvent('accountBalanceUpdated'));
        } else {
            const data = await response.json();
            showAlert(data.message || '출금에 실패했습니다.');
        }
    } catch (error) {
        console.error('Withdraw error:', error);
        showAlert('서버 연결에 실패했습니다.');
    }
}

// 거래내역 조회 (개선된 버전)
async function loadTransactionHistory() {
    const token = localStorage.getItem('accessToken');
    
    // 현재 기본 계좌 정보가 없으면 먼저 잔액 데이터 로드
    if (!currentPrimaryAccount) {
        await loadBalance();
    }
    
    // 여전히 계좌 정보가 없으면 예리 리턴
    if (!currentPrimaryAccount) {
        document.getElementById('transactionList').innerHTML = 
            '<p class="no-data">계좌 정보를 불러올 수 없습니다.</p>';
        return;
    }
    
    const accountNumber = currentPrimaryAccount.accountNumber;

    try {
        const response = await fetch(`/api/accounts/${accountNumber}/transactions`, {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (response.ok) {
            const transactions = await response.json();
            displayTransactions(transactions);
        } else {
            // 거래내역이 없는 경우
            document.getElementById('transactionList').innerHTML = 
                '<p class="no-data">거래내역이 없습니다.</p>';
        }
    } catch (error) {
        console.error('Transaction history error:', error);
        document.getElementById('transactionList').innerHTML = 
            '<p class="no-data">거래내역을 불러올 수 없습니다.</p>';
    }
}

// 거래내역 표시
function displayTransactions(transactions) {
    const listElement = document.getElementById('transactionList');
    
    if (!transactions || transactions.length === 0) {
        listElement.innerHTML = '<p class="no-data">거래내역이 없습니다.</p>';
        return;
    }

    const html = transactions.map(transaction => {
        // transactionType을 기준으로 출금/입금 판단
        const isWithdrawal = transaction.transactionType === 'WITHDRAWAL';
        const amountClass = isWithdrawal ? 'negative' : 'positive';
        const sign = isWithdrawal ? '-' : '+';
        const amount = Math.abs(transaction.amount); // 절댓값 사용
        
        return `
            <div class="transaction-item">
                <div class="transaction-info">
                    <div><strong>${transaction.description}</strong></div>
                    <div style="font-size: 12px; color: #666;">
                        ${new Date(transaction.createdAt).toLocaleString()}
                    </div>
                </div>
                <div class="transaction-amount ${amountClass}">
                    ${sign}${amount.toLocaleString()}원
                </div>
            </div>
        `;
    }).join('');
    
    listElement.innerHTML = html;
}

// 메인으로 이동
function goToMain() {
    window.location.href = '/main.html';
}

// 알림 메시지 표시
function showAlert(message) {
    const alertElement = document.getElementById('balanceAlert');
    if (alertElement) {
        alertElement.textContent = message;
        alertElement.className = 'alert alert-success show';
        
        // 3초 후 자동 숨김
        setTimeout(() => {
            alertElement.classList.remove('show');
        }, 3000);
    }
} 

// 토큰 만료 체크는 common.js에서 제공됨

// 백그라운드 토큰 체크
async function backgroundTokenCheck() {
    const accessToken = localStorage.getItem('accessToken');
    const refreshToken = localStorage.getItem('refreshToken');
    
    if (!accessToken || !refreshToken) {
        return;
    }

    try {
        const payload = JSON.parse(atob(accessToken.split('.')[1]));
        const expirationTime = payload.exp * 1000;
        const currentTime = Date.now();
        
        // 만료되었거나 곧 만료될 예정이면 갱신 시도
        if (currentTime >= expirationTime || (expirationTime - currentTime < 60000)) {
            console.log('🔄 백그라운드 토큰 갱신 시도...');
            const success = await window.refreshToken(); // common.js의 함수 사용
            if (!success) {
                console.log('❌ 백그라운드 토큰 갱신 실패, 로그아웃');
                showAutoLogoutPopup();
            }
        }
    } catch (error) {
        console.error('백그라운드 토큰 체크 오류:', error);
        showAutoLogoutPopup();
    }
}

// 로그아웃 함수
// 로그아웃 함수는 common.js에서 제공됨

// 토큰 갱신은 common.js에서 제공됨 