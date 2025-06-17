#!/bin/bash

echo "Starting Ship Proxy System..."

echo "Creating Docker network..."
docker network create proxy-network

echo "Starting Offshore Proxy Server..."
docker run -d \
  --name offshore-proxy-server \
  --network proxy-network \
  -p 9090:9090 \
  ship-proxy-server:latest
  
  
echo "Starting Ship Proxy Client..."
docker run -d \
  --name ship-proxy-client \
  --network proxy-network \
  -p 8080:8080 \
  -e SERVER_HOST=offshore-proxy-server \
  -e SERVER_PORT=9090 \
  ship-proxy-client:latest  



echo "=== System Started Successfully! ==="
echo "Ship Proxy Client: http://localhost:8080"
echo "Offshore Proxy Server: running on port 9090"
echo ""
echo "Test with:"
echo "curl -x http://localhost:8080 http://httpforever.com/"
echo ""
echo "To view logs:"
echo "docker logs -f ship-proxy-client"
echo "docker logs -f offshore-proxy-server"
echo ""
echo "To stop:"
echo "docker stop ship-proxy-client offshore-proxy-server"
echo "docker rm ship-proxy-client offshore-proxy-server"
echo "docker network rm proxy-network"
