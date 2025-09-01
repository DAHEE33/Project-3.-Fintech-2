# AWS 무중단 배포 가이드

## 1. 최적화 완료 ✅

- **불필요한 파일 정리**: 개발용 파일들 삭제
- **Dockerfile 최적화**: `--offline` 플래그로 빌드 속도 향상
- **무중단 배포 구조**: 8081/8082 포트 전환 방식
- **GitHub Actions**: 이미 구성됨

## 2. 파일 구조

```
├── docker-compose.yml          # 기본 개발용
├── docker-compose.prod.yml     # 무중단 배포용 (8081/8082)
├── Dockerfile                  # 최적화된 빌드
├── deploy.sh                   # 무중단 배포 스크립트
├── nginx-fintech.conf          # 호스트 Nginx 설정
├── env.rds.example            # RDS 환경변수 예시
└── .github/workflows/         # GitHub Actions (이미 구성됨)
```

## 3. 호스트 Nginx 설정

### EC2에서 실행:
```bash
# Nginx 설정 파일 복사
sudo cp nginx-fintech.conf /etc/nginx/conf.d/fintech.conf

# Nginx 설정 테스트
sudo nginx -t

# Nginx 재시작
sudo systemctl restart nginx
```

## 4. 무중단 배포

### 초기 배포:
```bash
# 첫 번째 인스턴스 시작 (8081)
docker-compose -f docker-compose.prod.yml up -d easypay-8081 db redis

# 헬스체크 확인
curl http://localhost:8081/actuator/health
```

### 업데이트 배포:
```bash
# 배포 스크립트 실행 권한 부여
chmod +x deploy.sh

# 무중단 배포 실행
./deploy.sh
```

## 5. RDS 사용 시

### 환경변수 설정:
```bash
# env.rds.example을 참고하여 .env 파일 생성
cp env.rds.example .env
# .env 파일에서 실제 RDS 정보로 수정
```

### docker-compose 실행 (RDS 사용):
```bash
# db 서비스 제외하고 실행
docker-compose -f docker-compose.prod.yml up easypay-8081 redis
```

## 6. 빌드 시간 단축 효과

- **--offline 플래그**: 의존성 다운로드 시간 제거
- **테스트 제외**: `-x test` 플래그로 테스트 실행 시간 제거
- **불필요한 파일 제외**: .dockerignore로 빌드 컨텍스트 크기 감소
- **멀티스테이지**: 최종 이미지 크기 최소화

## 7. 모니터링

- **Health Check**: `http://localhost:8081/actuator/health`
- **Nginx 상태**: `sudo systemctl status nginx`
- **컨테이너 상태**: `docker-compose -f docker-compose.prod.yml ps`
- **배포 로그**: `./deploy.sh` 실행 시 상세 로그 출력

## 8. GitHub Actions

이미 구성된 워크플로우:
- `ci-cd.yml`: CI/CD 파이프라인
- `deploy.yml`: 자동 배포
- `weekly-security.yml`: 보안 스캔
