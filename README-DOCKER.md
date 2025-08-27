# EasyPay Docker 개발 환경

## 개요

이 문서는 EasyPay 애플리케이션을 Docker를 사용하여 로컬 개발 환경에서 실행하는 방법을 설명합니다.

## 사전 요구사항

- Docker 및 Docker Compose 설치
- Git

## 환경별 실행 방법

### 방법 1: 자동 스크립트 사용 (권장)

```bash
# 개발 환경 (기본값)
./start.sh up

# 프로덕션 환경
ENV=prod ./start.sh up

# AWS RDS 환경
ENV=rds ./start.sh up
```

### 방법 2: 직접 명령어 사용

```bash
# 개발 환경 (docker-compose.yml + docker-compose.override.yml)
docker-compose up -d

# 프로덕션 환경 (docker-compose.yml만)
docker-compose -f docker-compose.yml up -d

# AWS RDS 환경
docker-compose -f docker-compose.rds.yml up -d
```

## 환경별 설정 파일

| 환경 | 파일 | 설명 |
|------|------|------|
| **개발** | `docker-compose.yml` + `docker-compose.override.yml` | 로컬 DB, 로그 마운트, dev 프로파일 |
| **프로덕션** | `docker-compose.yml` | 로컬 DB, prod 프로파일 |
| **AWS RDS** | `docker-compose.rds.yml` | RDS DB, prod 프로파일 |

## 빠른 시작

### 1. 환경 변수 설정 (선택사항)

```bash
# JWT 시크릿 설정 (기본값 사용 시 생략 가능)
export JWT_SECRET=your-custom-jwt-secret

# AWS RDS 사용 시
export DB_USERNAME=your-rds-username
export DB_PASSWORD=your-rds-password
```

### 2. 애플리케이션 실행

```bash
# 개발 환경으로 시작
./start.sh up

# 로그 확인
./start.sh logs
```

### 3. 접속 확인

- **애플리케이션**: http://localhost:8090
- **Nginx**: http://localhost:80
- **PostgreSQL**: localhost:5432 (개발/프로덕션 환경)
- **Redis**: localhost:6379

## 서비스 구성

### EasyPay 애플리케이션 (fintech-app)
- **포트**: 8090
- **프로파일**: dev (개발) / prod (프로덕션/RDS)
- **로그**: `./logs` 디렉토리에 마운트 (개발 환경)
- **DB 연결**: 
  - 개발/프로덕션: `jdbc:postgresql://db:5432/easypay`
  - RDS: `jdbc:postgresql://your-rds-endpoint.amazonaws.com:5432/easypay`

### PostgreSQL 데이터베이스 (fintech-db)
- **서비스명**: `db` (컨테이너 내부 호스트명)
- **포트**: 5432
- **데이터베이스**: easypay
- **사용자**: easypay
- **비밀번호**: easypay123
- **헬스체크**: 5초 간격, 3초 타임아웃, 10회 재시도
- **참고**: RDS 환경에서는 이 서비스가 제거됨

### Redis 캐시 (easypay-redis)
- **포트**: 6379
- **비밀번호**: 없음 (개발용)

### Nginx 리버스 프록시 (easypay-nginx)
- **포트**: 80, 443
- **설정**: nginx/nginx.conf

## 개발 명령어

### 스크립트 사용 (권장)
```bash
# 서비스 시작
./start.sh up

# 서비스 중지
./start.sh down

# 서비스 재시작
./start.sh restart

# 로그 확인
./start.sh logs

# 이미지 빌드
./start.sh build
```

### 직접 명령어 사용
```bash
# 개발 환경
docker-compose up -d
docker-compose down
docker-compose restart
docker-compose logs -f

# 프로덕션 환경
docker-compose -f docker-compose.yml up -d

# AWS RDS 환경
docker-compose -f docker-compose.rds.yml up -d
```

### 데이터베이스 접속
```bash
# PostgreSQL 접속 (개발/프로덕션 환경)
docker exec -it fintech-db psql -U easypay -d easypay

# Redis 접속
docker exec -it easypay-redis redis-cli
```

## 헬스 체크

```bash
# 애플리케이션 상태 확인
curl http://localhost:8090/actuator/health

# 데이터베이스 상태 확인 (개발/프로덕션 환경)
docker exec fintech-db pg_isready -U easypay -d easypay

# Redis 상태 확인
docker exec easypay-redis redis-cli ping
```

## 데이터 관리

### 데이터베이스 초기화 (개발/프로덕션 환경)
```bash
# 볼륨 삭제 (주의: 모든 데이터 삭제)
docker-compose down -v
docker-compose up -d
```

### 데이터베이스 백업 (개발/프로덕션 환경)
```bash
# 백업
docker exec fintech-db pg_dump -U easypay easypay > backup.sql

# 복구
docker exec -i fintech-db psql -U easypay easypay < backup.sql
```

## 환경별 특징

### 개발 환경 (`ENV=dev`)
- ✅ 로컬 PostgreSQL DB
- ✅ 로그 파일 마운트
- ✅ dev 프로파일
- ✅ 소스 코드 변경 감지 (선택사항)

### 프로덕션 환경 (`ENV=prod`)
- ✅ 로컬 PostgreSQL DB
- ✅ prod 프로파일
- ❌ 로그 파일 마운트 없음

### AWS RDS 환경 (`ENV=rds`)
- ✅ AWS RDS PostgreSQL
- ✅ prod 프로파일
- ❌ 로컬 DB 서비스 없음
- ❌ 로그 파일 마운트 없음

## 문제 해결

### 포트 충돌
```bash
# 사용 중인 포트 확인
netstat -tulpn | grep :8090

# 컨테이너 중지
./start.sh down
```

### 메모리 부족
```bash
# 시스템 리소스 확인
docker stats

# 불필요한 리소스 정리
docker system prune
```

### 로그 확인
```bash
# 애플리케이션 로그
docker logs fintech-app

# 데이터베이스 로그 (개발/프로덕션 환경)
docker logs fintech-db

# Nginx 로그
docker logs easypay-nginx
```

## 개발 팁

1. **환경 변수 변경 시**: `./start.sh restart`
2. **코드 변경 시**: `./start.sh build && ./start.sh up`
3. **데이터베이스 스키마 변경 시**: `docker-compose restart db`
4. **Nginx 설정 변경 시**: `docker-compose restart nginx`

## 파일 구조

```
├── docker-compose.yml           # 기본 설정 (프로덕션용)
├── docker-compose.override.yml  # 개발 환경 오버라이드 (자동 적용)
├── docker-compose.rds.yml       # AWS RDS 환경용
├── start.sh                    # 환경별 실행 스크립트
├── Dockerfile                  # 애플리케이션 이미지
├── .dockerignore              # 빌드 제외 파일
├── nginx/                    # Nginx 설정
│   ├── nginx.conf
│   └── ssl/
└── logs/                     # 애플리케이션 로그 (개발 환경)
```
