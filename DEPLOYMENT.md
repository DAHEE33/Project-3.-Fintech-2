# AWS 배포 가이드

## 1. 빌드 최적화 완료 ✅

- **nginx/ 폴더 삭제**: 컨테이너 Nginx 제거
- **Dockerfile 최적화**: 멀티스테이지 빌드, 테스트 제외
- **docker-compose.yml 최소화**: nginx 서비스 제거, RDS 대비 환경변수 설정
- **.dockerignore 추가**: 불필요한 파일 제외로 빌드 속도 향상

## 2. 호스트 Nginx 설정

### EC2에서 실행:
```bash
# Nginx 설정 파일 복사
sudo cp nginx-fintech.conf /etc/nginx/conf.d/fintech.conf

# Nginx 설정 테스트
sudo nginx -t

# Nginx 재시작
sudo systemctl restart nginx
```

## 3. RDS 사용 시

### 환경변수 설정:
```bash
# env.rds.example을 참고하여 .env 파일 생성
cp env.rds.example .env
# .env 파일에서 실제 RDS 정보로 수정
```

### docker-compose 실행 (RDS 사용):
```bash
# db 서비스 제외하고 실행
docker-compose up easypay redis
```

## 4. 빌드 시간 단축 효과

- **테스트 제외**: `-x test` 플래그로 테스트 실행 시간 제거
- **의존성 캐싱**: gradle dependencies를 별도 레이어로 분리
- **불필요한 파일 제외**: .dockerignore로 빌드 컨텍스트 크기 감소
- **멀티스테이지**: 최종 이미지 크기 최소화

## 5. 배포 명령어

```bash
# 이미지 빌드
docker build -t easypay:latest .

# 컨테이너 실행
docker-compose up -d easypay redis

# 로그 확인
docker-compose logs -f easypay
```

## 6. 모니터링

- **Health Check**: `http://localhost:8090/actuator/health`
- **Nginx 상태**: `sudo systemctl status nginx`
- **컨테이너 상태**: `docker-compose ps`
