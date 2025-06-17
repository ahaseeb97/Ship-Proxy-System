#!/bin/bash

echo "Stopping Ship Proxy System..."
docker stop ship-proxy-client offshore-proxy-server
docker rm ship-proxy-client offshore-proxy-server
docker network rm proxy-network

echo "System stopped!"
