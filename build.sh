#!/bin/bash

echo "Building Ship Proxy System..."

# Compile the codes
javac -d client ProxyClient.java
javac -d server ProxyServer.java

# Build Docker images
echo "Building proxy server Docker image..."
docker build -f Dockerfile.server -t ship-proxy-server:latest .

echo "Building proxy client Docker image..."
docker build -f Dockerfile.client -t ship-proxy-client:latest .

echo "Build completed successfully!"
