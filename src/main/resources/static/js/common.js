/**
 * 공통 JavaScript 모듈
 * 자동로그아웃 팝업, 로그아웃 기능 등을 제공
 */

// 전역 변수
let logoutInProgress = false;
let clientConfig = {
    accessCheckInterval: 5000,    // 기본값 5초
    backgroundCheckInterval: 10000 // 기본값 10초
};

/**
 * 서버에서 클라이언트 설정 가져오기
 */
async function loadClientConfig() {
    try {
        const response = await fetch('/api/auth/client-config');
        if (response.ok) {
            const config = await response.json();
            clientConfig = {
                accessCheckInterval: config.accessCheckInterval || 5000,
                backgroundCheckInterval: config.backgroundCheckInterval || 10000
            };
            console.log('✅ 클라이언트 설정 로드 완료:', clientConfig);
        }
    } catch (error) {
        console.warn('⚠️ 클라이언트 설정 로드 실패, 기본값 사용:', error);
    }
}

/**
 * 자동로그아웃 팝업 표시
 */
function showAutoLogoutPopup() {
    // 이미 팝업이 있으면 제거
    if (document.getElementById('autoLogoutPopup')) {
        document.getElementById('autoLogoutPopup').remove();
    }
    
    const popup = document.createElement('div');
    popup.id = 'autoLogoutPopup';
    popup.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background-color: rgba(0, 0, 0, 0.5);
        display: flex;
        justify-content: center;
        align-items: center;
        z-index: 9999;
    `;
    
    popup.innerHTML = `
        <div style="
            background: white;
            padding: 30px;
            border-radius: 10px;
            text-align: center;
            max-width: 400px;
            width: 90%;
        ">
            <h3 style="color: #e74c3c; margin-bottom: 20px;">세션 만료</h3>
            <p style="margin-bottom: 25px; line-height: 1.5;">
                보안을 위해 자동으로 로그아웃되었습니다.<br>
                다시 로그인해 주세요.
            </p>
            <button onclick="this.closest('#autoLogoutPopup').remove(); window.location.href='/index.html';"
                    style="
                        background: #3498db;
                        color: white;
                        border: none;
                        padding: 12px 24px;
                        border-radius: 5px;
                        cursor: pointer;
                        font-size: 14px;
                    ">
                로그인 페이지로 이동
            </button>
        </div>
    `;
    
    document.body.appendChild(popup);
}

/**
 * 자동로그아웃 팝업 제거
 */
function hideAutoLogoutPopup() {
    if (document.getElementById('autoLogoutPopup')) {
        document.getElementById('autoLogoutPopup').remove();
    }
}

/**
 * 로그아웃 처리
 */
async function logout() {
    // 이미 로그아웃 진행 중이면 중복 실행 방지
    if (logoutInProgress) {
        return;
    }
    
    try {
        logoutInProgress = true;
        
        // 로컬 스토리지에서 토큰 제거
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('userPhone');
        
        // 서버에 로그아웃 요청
        const response = await fetch('/api/auth/logout', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${localStorage.getItem('accessToken') || ''}`
            }
        });
        
        // 로그인 페이지로 리다이렉트
        window.location.href = '/index.html';
        
    } catch (error) {
        console.error('로그아웃 중 오류 발생:', error);
        // 오류가 발생해도 로컬 스토리지는 정리하고 로그인 페이지로 이동
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('userPhone');
        window.location.href = '/index.html';
    } finally {
        logoutInProgress = false;
    }
}

/**
 * 토큰 만료 체크 및 자동로그아웃 처리
 */
function checkTokenExpiration() {
    const accessToken = localStorage.getItem('accessToken');
    const refreshToken = localStorage.getItem('refreshToken');
    
    if (!accessToken || !refreshToken) {
        console.log('토큰이 없어 자동로그아웃 팝업 표시');
        showAutoLogoutPopup();
        return;
    }
    
    try {
        // JWT 토큰 디코딩 (헤더와 페이로드만)
        const payload = JSON.parse(atob(accessToken.split('.')[1]));
        const expirationTime = payload.exp * 1000;
        const currentTime = Date.now();
        
        const remainingSeconds = Math.floor((expirationTime - currentTime) / 1000);
        
        console.log('토큰 체크:', {
            remainingSeconds: remainingSeconds
        });
        
        // 만료되었으면 갱신 시도
        if (currentTime >= expirationTime) {
            console.log('Access Token 만료됨, 갱신 시도...');
            window.refreshToken().then(success => {
                if (!success) {
                    console.log('토큰 갱신 실패, 자동로그아웃 팝업 표시');
                    showAutoLogoutPopup();
                }
            });
        } else if (remainingSeconds < 10) {
            // 10초 이내로 만료될 예정이면 미리 갱신 시도
            console.log('Access Token 곧 만료됨, 미리 갱신 시도...');
            window.refreshToken().then(success => {
                if (!success) {
                    console.log('토큰 갱신 실패, 자동로그아웃 팝업 표시');
                    showAutoLogoutPopup();
                }
            });
        }
    } catch (error) {
        console.error('토큰 파싱 오류:', error);
        showAutoLogoutPopup();
    }
}

/**
 * 토큰 갱신 시도
 */
async function refreshToken() {
    // 로그아웃 진행 중이면 갱신 시도하지 않음
    if (logoutInProgress) {
        console.log('🚪 로그아웃 진행 중... 갱신 시도 중단');
        return false;
    }
    
    const refreshToken = localStorage.getItem('refreshToken');
    
    if (!refreshToken) {
        console.log('❌ Refresh Token이 없습니다.');
        showAutoLogoutPopup();
        return false;
    }
    
    try {
        console.log('🔄 === Refresh Token으로 토큰 갱신 시도 ===');
        
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
            
            console.log('✅ === 토큰 갱신 성공! ===');
            return true;
        } else {
            console.log('❌ Refresh Token 갱신 실패:', response.status);
            const errorText = await response.text();
            console.log('❌ 에러 응답:', errorText);
            
            if (response.status === 400 && (errorText.includes('EXPIRED_REFRESH_TOKEN') || errorText.includes('INVALID_REFRESH_TOKEN'))) {
                console.log('🚪 Refresh Token 만료/무효로 즉시 로그아웃 실행');
                showAutoLogoutPopup();
                return false;
            }
            
            showAutoLogoutPopup();
            return false;
        }
    } catch (error) {
        console.error('❌ 토큰 갱신 중 오류:', error);
        showAutoLogoutPopup();
        return false;
    }
}

/**
 * 인증이 필요한 API 요청을 위한 헤더 생성
 */
function getAuthHeaders() {
    const accessToken = localStorage.getItem('accessToken');
    return {
        'Content-Type': 'application/json',
        'Authorization': accessToken ? `Bearer ${accessToken}` : ''
    };
}

/**
 * 공통 API 요청 함수 (토큰 갱신 포함)
 */
async function authenticatedFetch(url, options = {}) {
    const headers = getAuthHeaders();
    
    // 기존 헤더와 병합
    options.headers = { ...headers, ...options.headers };
    
    try {
        let response = await fetch(url, options);
        
        // 401 에러 시 토큰 갱신 시도
        if (response.status === 401) {
            const refreshSuccess = await window.refreshToken();
            if (refreshSuccess) {
                // 토큰 갱신 성공 시 재시도
                options.headers = getAuthHeaders();
                response = await fetch(url, options);
            } else {
                // 토큰 갱신 실패 시 로그아웃
                return null;
            }
        }
        
        return response;
    } catch (error) {
        console.error('API 요청 오류:', error);
        return null;
    }
}

// 전역 함수로 노출
window.showAutoLogoutPopup = showAutoLogoutPopup;
window.hideAutoLogoutPopup = hideAutoLogoutPopup;
window.logout = logout;
window.checkTokenExpiration = checkTokenExpiration;
window.refreshToken = refreshToken;
window.getAuthHeaders = getAuthHeaders;
window.authenticatedFetch = authenticatedFetch;
window.loadClientConfig = loadClientConfig;
window.clientConfig = clientConfig; 