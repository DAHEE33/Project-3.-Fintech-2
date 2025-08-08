// 토큰 갱신 중복 방지 플래그
let isRefreshing = false;

// 페이지 로드 시 실행
document.addEventListener('DOMContentLoaded', function() {
    // 현재 페이지가 로그인 관련 페이지인지 확인
    const isLoginPage = window.location.pathname.includes('login') || 
                       window.location.pathname.includes('register') || 
                       window.location.pathname === '/' || 
                       window.location.pathname === '/index.html';
    
    // 로그인 페이지가 아닐 때만 토큰 체크 실행
    if (!isLoginPage) {
        // 설정에서 체크 주기 가져오기 (기본값: 30초, 60초)
        const accessCheckInterval = 30000; // 30초
        const backgroundCheckInterval = 60000; // 60초
        
        // 토큰 만료 체크 시작
        setInterval(checkTokenExpiration, accessCheckInterval);
        
        // 백그라운드 토큰 체크 시작
        setInterval(backgroundTokenCheck, backgroundCheckInterval);
        
        // 초기 토큰 체크
        checkTokenExpiration();
        backgroundTokenCheck();
    }
    
    checkAuth();
    loadBalance();
    loadAlarms();
    loadTransactions();
});

// 페이징 관련 변수
let currentPage = 1;
let pageSize = 10;
let allAlarms = [];
let filteredAlarms = [];

// 인증 확인
function checkAuth() {
    const token = localStorage.getItem('accessToken');
    if (!token) {
        alert('로그인이 필요합니다.');
        window.location.href = '/index.html';
        return;
    }
}

// 토큰 만료 체크
async function checkTokenExpiration() {
    const accessToken = localStorage.getItem('accessToken');
    const refreshToken = localStorage.getItem('refreshToken');
    
    if (!accessToken || !refreshToken) {
        console.log('토큰이 없어 로그인 페이지로 이동');
        logout();
        return;
    }

    try {
        const payload = JSON.parse(atob(accessToken.split('.')[1]));
        const expirationTime = payload.exp * 1000;
        const currentTime = Date.now();
        
        console.log('알림 페이지 토큰 체크:', {
            remainingSeconds: Math.floor((expirationTime - currentTime) / 1000)
        });
        
        // 만료되었으면 갱신 시도
        if (currentTime >= expirationTime) {
            console.log('Access Token 만료됨, 갱신 시도...');
            const success = await tryRefreshToken();
            if (!success) {
                console.log('토큰 갱신 실패, 로그아웃');
                showAutoLogoutPopup();
            }
        }
    } catch (error) {
        console.error('토큰 체크 오류:', error);
        showAutoLogoutPopup();
    }
}

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
            console.log('백그라운드 토큰 갱신 시도...');
            const success = await tryRefreshToken();
            if (!success) {
                console.log('백그라운드 토큰 갱신 실패, 로그아웃');
                showAutoLogoutPopup();
            }
        }
    } catch (error) {
        console.error('백그라운드 토큰 체크 오류:', error);
        showAutoLogoutPopup();
    }
}

// 로그아웃 함수는 common.js에서 제공됨

// Refresh Token으로 토큰 갱신 시도
async function tryRefreshToken() {
    // 이미 갱신 중이면 중복 실행 방지
    if (isRefreshing) {
        console.log('이미 토큰 갱신 중... 대기');
        return false;
    }
    
    // 로그아웃 진행 중이면 갱신 시도하지 않음
    if (logoutInProgress) {
        console.log('로그아웃 진행 중... 갱신 시도 중단');
        return false;
    }
    
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
        console.log('Refresh Token이 없습니다.');
        return false;
    }

    isRefreshing = true;
    
    try {
        console.log('=== Refresh Token으로 토큰 갱신 시도 ===');
        
        const response = await fetch('/api/auth/refresh', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                refreshToken: refreshToken
            })
        });

        if (response.ok) {
            const data = await response.json();
            
            localStorage.setItem('accessToken', data.accessToken);
            localStorage.setItem('refreshToken', data.refreshToken);
            
            console.log('=== 토큰 갱신 성공! ===');
            return true;
        } else {
            console.log('Refresh Token 갱신 실패:', response.status);
            const errorText = await response.text();
            console.log('에러 응답:', errorText);
            
            if (response.status === 400 && (errorText.includes('EXPIRED_REFRESH_TOKEN') || errorText.includes('INVALID_REFRESH_TOKEN'))) {
                console.log('Refresh Token 만료/무효로 즉시 로그아웃 실행');
                showAutoLogoutPopup();
                return false;
            }
            
            return false;
        }
    } catch (error) {
        console.error('토큰 갱신 중 오류:', error);
        showAutoLogoutPopup();
        return false;
    } finally {
        isRefreshing = false;
    }
}

// 잔액 로드
async function loadBalance() {
    const token = localStorage.getItem('accessToken');
    const accountNumber = localStorage.getItem('accountNumber');
    
    if (!accountNumber) {
        document.getElementById('currentBalance').textContent = '계좌번호 없음';
        document.getElementById('accountInfo').textContent = '계좌번호: -';
        console.warn('Account number not found in localStorage');
        return;
    }
    
    try {
        const response = await fetch(`/api/accounts/${accountNumber}/balance`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const data = await response.json();
            const balance = new Intl.NumberFormat('ko-KR').format(data.balance);
            document.getElementById('currentBalance').textContent = `${balance}원`;
            document.getElementById('accountInfo').textContent = `계좌번호: ${accountNumber}`;
        } else if (response.status === 404) {
            document.getElementById('currentBalance').textContent = '계좌를 찾을 수 없음';
            document.getElementById('accountInfo').textContent = `계좌번호: ${accountNumber}`;
        } else {
            document.getElementById('currentBalance').textContent = '잔액 조회 실패';
            document.getElementById('accountInfo').textContent = `계좌번호: ${accountNumber}`;
        }
    } catch (error) {
        console.error('Balance load error:', error);
        document.getElementById('currentBalance').textContent = '잔액 조회 실패';
        document.getElementById('accountInfo').textContent = `계좌번호: ${accountNumber}`;
    }
}

// 탭 전환
function showTab(tabName) {
    // 모든 탭 버튼 비활성화
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    
    // 모든 탭 콘텐츠 숨김
    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.remove('active');
    });
    
    // 클릭된 탭 활성화
    event.target.classList.add('active');
    document.getElementById(tabName + '-tab').classList.add('active');
    
    // 탭에 따라 데이터 로드
    if (tabName === 'alarms') {
        loadAlarms();
        loadBalance(); // 알림 탭으로 이동 시 잔액 새로고침
    } else if (tabName === 'transactions') {
        loadTransactions();
    }
}

// 알림 읽음 처리
async function markAlarmsAsRead() {
    const token = localStorage.getItem('accessToken');
    
    if (!token) {
        return;
    }
    
    try {
        const response = await fetch('/api/alarms/mark-read', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            console.log('알림을 읽음 처리했습니다.');
            // 알림 개수 리셋을 위해 localStorage에 플래그 설정
            localStorage.setItem('alarmsReadAt', new Date().toISOString());
        } else {
            console.error('알림 읽음 처리 실패:', response.status);
        }
        
    } catch (error) {
        console.error('알림 읽음 처리 오류:', error);
    }
}

// 알람 목록 로드
async function loadAlarms() {
    const token = localStorage.getItem('accessToken');
    
    try {
        const response = await fetch('/api/alarms/list', {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const data = await response.json();
            console.log('Alarms response:', data);
            if (data.success) {
                allAlarms = data.alarms || [];
                filteredAlarms = [...allAlarms];
                currentPage = 1;
                console.log('Loaded alarms:', allAlarms.length);
                displayAlarmsPage();
                // 알림을 로드한 후 읽음 처리
                markAlarmsAsRead();
            } else {
                console.error('Alarm load error:', data.message);
                showAlert('알람을 불러올 수 없습니다.');
            }
        } else {
            console.error('Alarm load error:', response.status);
            showAlert('알람을 불러올 수 없습니다.');
        }
    } catch (error) {
        console.error('Alarm load error:', error);
        showAlert('알람을 불러올 수 없습니다.');
    }
}

// 현재 페이지의 알림 표시
function displayAlarmsPage() {
    const startIndex = (currentPage - 1) * pageSize;
    const endIndex = startIndex + pageSize;
    const pageAlarms = filteredAlarms.slice(startIndex, endIndex);
    
    displayAlarms(pageAlarms);
    updatePagination();
}

// 페이징 업데이트
function updatePagination() {
    const totalPages = Math.ceil(filteredAlarms.length / pageSize);
    const pagination = document.getElementById('alarmPagination');
    const prevBtn = document.getElementById('prevPage');
    const nextBtn = document.getElementById('nextPage');
    const pageInfo = document.getElementById('pageInfo');
    
    if (totalPages <= 1) {
        pagination.style.display = 'none';
        return;
    }
    
    pagination.style.display = 'flex';
    prevBtn.disabled = currentPage <= 1;
    nextBtn.disabled = currentPage >= totalPages;
    pageInfo.textContent = `${currentPage} / ${totalPages}`;
}

// 페이지 변경
function changePage(direction) {
    const totalPages = Math.ceil(filteredAlarms.length / pageSize);
    const newPage = currentPage + direction;
    
    if (newPage >= 1 && newPage <= totalPages) {
        currentPage = newPage;
        displayAlarmsPage();
    }
}

// 거래내역 로드
async function loadTransactions() {
    const token = localStorage.getItem('accessToken');
    const accountNumber = localStorage.getItem('accountNumber');
    
    if (!accountNumber) {
        showAlert('계좌번호를 찾을 수 없습니다.');
        return;
    }
    
    try {
        const response = await fetch(`/api/accounts/${accountNumber}/transactions`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const transactions = await response.json();
            displayTransactions(transactions);
        } else {
            console.error('Transaction load error:', response.status);
            showAlert('거래내역을 불러올 수 없습니다.');
        }
    } catch (error) {
        console.error('Transaction load error:', error);
        showAlert('거래내역을 불러올 수 없습니다.');
    }
}

// 거래내역 표시
function displayTransactions(transactions) {
    const listElement = document.getElementById('transactionList');
    
    if (!transactions || transactions.length === 0) {
        listElement.innerHTML = '<div class="no-alarms">거래내역이 없습니다.</div>';
        return;
    }

    const html = transactions.map(transaction => {
        const typeClass = getTransactionTypeClass(transaction.transactionType);
        // transactionType을 기준으로 출금/입금 판단
        const isWithdrawal = transaction.transactionType === 'WITHDRAWAL';
        const amountClass = isWithdrawal ? 'negative' : 'positive';
        const sign = isWithdrawal ? '-' : '+';
        const amount = Math.abs(transaction.amount); // 절댓값 사용
        const amountText = `${sign}${formatAmount(amount)}`;
        
        return `
            <div class="transaction-item ${typeClass}">
                <div class="transaction-header">
                    <div class="transaction-type">${getTransactionTypeIcon(transaction.transactionType)} ${getTransactionTypeName(transaction.transactionType)}</div>
                    <div class="transaction-amount ${amountClass}">${amountText}원</div>
                </div>
                <div class="transaction-description">${transaction.description || '거래내역'}</div>
                <div class="transaction-details">
                    <span>잔액: ${formatAmount(transaction.balanceAfter)}원</span>
                    <span>${formatTime(new Date(transaction.createdAt))}</span>
                </div>
            </div>
        `;
    }).join('');
    
    listElement.innerHTML = html;
}

// 거래 유형별 CSS 클래스
function getTransactionTypeClass(type) {
    switch (type) {
        case 'WITHDRAWAL': return 'withdrawal';
        case 'DEPOSIT': return 'deposit';
        case 'TRANSFER': return 'transfer';
        case 'PAYMENT': return 'payment';
        case 'REFUND': return 'refund';
        default: return '';
    }
}

// 거래 유형별 아이콘
function getTransactionTypeIcon(type) {
    switch (type) {
        case 'WITHDRAWAL': return '💸';
        case 'DEPOSIT': return '💰';
        case 'TRANSFER': return '🔄';
        case 'PAYMENT': return '💳';
        case 'REFUND': return '↩️';
        default: return '📊';
    }
}

// 거래 유형별 이름
function getTransactionTypeName(type) {
    switch (type) {
        case 'WITHDRAWAL': return '출금';
        case 'DEPOSIT': return '입금';
        case 'TRANSFER': return '이체';
        case 'PAYMENT': return '결제';
        case 'REFUND': return '환불';
        default: return '거래';
    }
}

// 금액 포맷팅
function formatAmount(amount) {
    return new Intl.NumberFormat('ko-KR').format(Math.abs(amount));
}

// 알람 메시지에서 금액 형식 수정 (소수점 제거 및 천 단위 콤마 추가)
function formatAlarmMessage(message) {
    // 금액 패턴 찾기 (예: 1234567.00원, 1234567원, 1,234,567.00원 등)
    return message.replace(/(\d+(?:,\d{3})*(?:\.\d+)?)(원)/g, function(match, amount, won) {
        // 콤마와 소수점 제거하여 순수 숫자만 추출
        const cleanNumber = amount.replace(/[,\.]/g, '').replace(/\.\d+$/, '');
        // 정수로 변환 후 천 단위 콤마 추가
        const formattedNumber = parseInt(cleanNumber).toLocaleString('ko-KR');
        return formattedNumber + won;
    });
}

// 알람 표시
function displayAlarms(alarms) {
    const listElement = document.getElementById('alarmList');
    
    if (!alarms || alarms.length === 0) {
        listElement.innerHTML = '<div class="no-alarms">알람이 없습니다.</div>';
        return;
    }

    const html = alarms.map(alarm => {
        const levelClass = getLevelClass(alarm.level);
        const typeIcon = getTypeIcon(alarm.type);
        // 알람 메시지의 금액 형식 수정
        const formattedMessage = formatAlarmMessage(alarm.message);
        
        return `
            <div class="alarm-item ${levelClass}">
                <div class="alarm-header">
                    <div class="alarm-type">${typeIcon} ${getTypeName(alarm.type)}</div>
                    <div class="alarm-time">${formatTime(new Date(alarm.timestamp))}</div>
                </div>
                <div class="alarm-message">${formattedMessage}</div>
            </div>
        `;
    }).join('');
    
    listElement.innerHTML = html;
}

// 알림 필터링
async function filterAlarms(category, event) {
    console.log('Filtering alarms by category:', category);
    
    // 필터 버튼 스타일 업데이트
    document.querySelectorAll('.filter-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    if (event && event.target) {
        event.target.classList.add('active');
        console.log('Updated button styling for:', event.target.textContent);
    }
    
    const token = localStorage.getItem('accessToken');
    
    try {
        const response = await fetch(`/api/alarms/list?category=${category}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            const data = await response.json();
            console.log('Filter response:', data);
            if (data.success) {
                filteredAlarms = data.alarms || [];
                currentPage = 1; // 필터 변경 시 첫 페이지로
                console.log('Filtered alarms count:', filteredAlarms.length);
                displayAlarmsPage();
            } else {
                console.error('Alarm filter error:', data.message);
                showAlert('알람 필터링에 실패했습니다.');
            }
        } else {
            console.error('Alarm filter error:', response.status);
            showAlert('알람 필터링에 실패했습니다.');
        }
    } catch (error) {
        console.error('Alarm filter error:', error);
        showAlert('알람 필터링에 실패했습니다.');
    }
}

// 레벨별 CSS 클래스
function getLevelClass(level) {
    switch (level) {
        case 'error': return 'error';
        case 'warning': return 'warning';
        case 'info': return 'success';
        default: return '';
    }
}

// 타입별 아이콘
function getTypeIcon(type) {
    switch (type) {
        case 'BALANCE_CHANGE': return '💰';
        case 'INSUFFICIENT_BALANCE': return '⚠️';
        case 'LOGIN_FAILURE': return '🔐';
        case 'ACCOUNT_LOCK': return '🚫';
        case 'SYSTEM_ERROR': return '💥';
        case 'LOGIN_SUCCESS': return '✅';
        case 'SUSPICIOUS_TRANSACTION': return '🚨';
        case 'LARGE_TRANSACTION': return '💎';
        case 'FREQUENT_TRANSACTION': return '⚡';
        default: return '🔔';
    }
}

// 타입별 이름
function getTypeName(type) {
    switch (type) {
        case 'BALANCE_CHANGE': return '잔액 변동';
        case 'INSUFFICIENT_BALANCE': return '잔액 부족';
        case 'LOGIN_FAILURE': return '로그인 실패';
        case 'ACCOUNT_LOCK': return '계정 잠금';
        case 'SYSTEM_ERROR': return '시스템 오류';
        case 'LOGIN_SUCCESS': return '로그인 성공';
        case 'SUSPICIOUS_TRANSACTION': return '이상거래 감지';
        case 'LARGE_TRANSACTION': return '큰 금액 거래';
        case 'FREQUENT_TRANSACTION': return '빈번한 거래';
        default: return '알람';
    }
}

// 시간 포맷팅
function formatTime(timestamp) {
    const now = new Date();
    const diff = now - timestamp;
    
    if (diff < 60 * 1000) return '방금 전';
    if (diff < 60 * 60 * 1000) return `${Math.floor(diff / (60 * 1000))}분 전`;
    if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / (60 * 60 * 1000))}시간 전`;
    return timestamp.toLocaleDateString();
}

// 메인으로 이동
function goToMain() {
    window.location.href = '/main.html';
}

// 알림 메시지 표시
function showAlert(message) {
    const alertElement = document.getElementById('alarmAlert');
    if (alertElement) {
        alertElement.textContent = message;
        alertElement.className = 'alert alert-success show';
        
        // 3초 후 자동 숨김
        setTimeout(() => {
            alertElement.classList.remove('show');
        }, 3000);
    }
} 