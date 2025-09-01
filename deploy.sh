#!/bin/bash

set -e

# Configuration
APP_NAME="easypay"
CURRENT_PORT="8081"
NEW_PORT="8082"
HEALTH_CHECK_URL="http://localhost:${NEW_PORT}/actuator/health"
HEALTH_CHECK_TIMEOUT=60
HEALTH_CHECK_INTERVAL=5

echo "🚀 Starting zero-downtime deployment..."

# Function to check if port is available
check_port_available() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null ; then
        echo "❌ Port $port is already in use"
        exit 1
    fi
}

# Function to check health
check_health() {
    local port=$1
    local url="http://localhost:${port}/actuator/health"
    
    echo "🔍 Checking health at $url"
    
    local start_time=$(date +%s)
    while true; do
        if curl -f -s "$url" > /dev/null; then
            echo "✅ Health check passed for port $port"
            return 0
        fi
        
        local current_time=$(date +%s)
        local elapsed=$((current_time - start_time))
        
        if [ $elapsed -gt $HEALTH_CHECK_TIMEOUT ]; then
            echo "❌ Health check failed for port $port after ${HEALTH_CHECK_TIMEOUT}s"
            return 1
        fi
        
        echo "⏳ Health check failed, retrying in ${HEALTH_CHECK_INTERVAL}s... (${elapsed}s elapsed)"
        sleep $HEALTH_CHECK_INTERVAL
    done
}

# Function to switch nginx upstream
switch_nginx_upstream() {
    local new_port=$1
    echo "🔄 Switching Nginx upstream to port $new_port"
    
    # Update nginx config
    sed -i "s/server 127.0.0.1:8081 max_fails=3 fail_timeout=30s;/server 127.0.0.1:${new_port} max_fails=3 fail_timeout=30s;/" /etc/nginx/conf.d/fintech.conf
    sed -i "s/server 127.0.0.1:8082 max_fails=3 fail_timeout=30s backup;/server 127.0.0.1:$((new_port == 8081 ? 8082 : 8081)) max_fails=3 fail_timeout=30s backup;/" /etc/nginx/conf.d/fintech.conf
    
    # Test nginx config
    if nginx -t; then
        # Reload nginx
        nginx -s reload
        echo "✅ Nginx upstream switched to port $new_port"
    else
        echo "❌ Nginx configuration test failed"
        exit 1
    fi
}

# Function to stop old container
stop_old_container() {
    local old_port=$1
    echo "🛑 Stopping old container on port $old_port"
    
    if docker-compose -f docker-compose.prod.yml stop easypay-$old_port 2>/dev/null; then
        echo "✅ Old container stopped"
    else
        echo "⚠️  No old container to stop"
    fi
}

# Main deployment logic
echo "📋 Deployment configuration:"
echo "   - Current port: $CURRENT_PORT"
echo "   - New port: $NEW_PORT"
echo "   - Health check timeout: ${HEALTH_CHECK_TIMEOUT}s"

# Check if new port is available
check_port_available $NEW_PORT

# Build new image
echo "🔨 Building new Docker image..."
docker-compose -f docker-compose.prod.yml build easypay-$NEW_PORT

# Start new container
echo "🚀 Starting new container on port $NEW_PORT..."
docker-compose -f docker-compose.prod.yml up -d easypay-$NEW_PORT

# Wait for new container to be healthy
echo "⏳ Waiting for new container to be healthy..."
if check_health $NEW_PORT; then
    echo "✅ New container is healthy"
else
    echo "❌ New container failed health check"
    docker-compose -f docker-compose.prod.yml logs easypay-$NEW_PORT
    docker-compose -f docker-compose.prod.yml stop easypay-$NEW_PORT
    exit 1
fi

# Switch nginx upstream
switch_nginx_upstream $NEW_PORT

# Stop old container
stop_old_container $CURRENT_PORT

echo "🎉 Zero-downtime deployment completed successfully!"
echo "   - New version is running on port $NEW_PORT"
echo "   - Old version stopped on port $CURRENT_PORT"
echo "   - Nginx is now routing traffic to port $NEW_PORT"
