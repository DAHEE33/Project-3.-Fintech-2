#!/bin/bash

# EasyPay Docker 환경별 실행 스크립트

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# 환경 변수 확인
ENV=${ENV:-dev}
COMPOSE_FILE=""

case $ENV in
    "dev"|"development")
        log_info "개발 환경으로 실행합니다..."
        COMPOSE_FILE="docker-compose.yml"
        # docker-compose.override.yml이 자동으로 적용됨
        ;;
    "prod"|"production")
        log_info "프로덕션 환경으로 실행합니다..."
        COMPOSE_FILE="docker-compose.yml"
        ;;
    "rds"|"aws")
        log_info "AWS RDS 환경으로 실행합니다..."
        COMPOSE_FILE="docker-compose.rds.yml"
        ;;
    *)
        log_error "지원하지 않는 환경입니다: $ENV"
        log_info "사용 가능한 환경: dev, prod, rds"
        exit 1
        ;;
esac

# 명령어 확인
case $1 in
    "up"|"start")
        log_step "Docker Compose 서비스 시작"
        if [ "$COMPOSE_FILE" = "docker-compose.rds.yml" ]; then
            docker-compose -f $COMPOSE_FILE up -d
        else
            docker-compose up -d
        fi
        ;;
    "down"|"stop")
        log_step "Docker Compose 서비스 중지"
        if [ "$COMPOSE_FILE" = "docker-compose.rds.yml" ]; then
            docker-compose -f $COMPOSE_FILE down
        else
            docker-compose down
        fi
        ;;
    "restart")
        log_step "Docker Compose 서비스 재시작"
        if [ "$COMPOSE_FILE" = "docker-compose.rds.yml" ]; then
            docker-compose -f $COMPOSE_FILE restart
        else
            docker-compose restart
        fi
        ;;
    "logs")
        log_step "로그 확인"
        if [ "$COMPOSE_FILE" = "docker-compose.rds.yml" ]; then
            docker-compose -f $COMPOSE_FILE logs -f
        else
            docker-compose logs -f
        fi
        ;;
    "build")
        log_step "이미지 빌드"
        if [ "$COMPOSE_FILE" = "docker-compose.rds.yml" ]; then
            docker-compose -f $COMPOSE_FILE build
        else
            docker-compose build
        fi
        ;;
    *)
        log_error "지원하지 않는 명령어입니다: $1"
        log_info "사용 가능한 명령어: up, down, restart, logs, build"
        log_info "사용법: ./start.sh [명령어]"
        log_info "환경 설정: ENV=dev ./start.sh up"
        exit 1
        ;;
esac

log_info "완료!"
