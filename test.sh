#!/bin/bash

echo "Testing Ship Proxy System..."

# Wait for services to be ready
echo "Waiting for services to start..."
sleep 10

# Test basic functionality
echo "Testing HTTP request..."
curl -x http://localhost:8080 http://httpforever.com/ -v

echo ""
echo "Testing multiple requests (sequential processing)..."
for i in {1..3}; do
    echo "Request $i:"
    curl -x http://localhost:8080 http://httpforever.com/ -s -o /dev/null -w "Response time: %{time_total}s\n"
done
