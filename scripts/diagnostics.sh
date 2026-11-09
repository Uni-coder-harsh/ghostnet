#!/usr/bin/env bash
# GhostNet Local Network Diagnostic Utility

PORT="${PORT:-8080}"

echo "=========================================================="
echo " GhostNet Network Connectivity Diagnostic Probe"
echo " Target Port: $PORT"
echo "=========================================================="

echo "Active Network Interfaces:"
ip -br addr show || ifconfig

echo ""
echo "Testing local application reachability (port $PORT)..."
if nc -z -w2 127.0.0.1 "$PORT" 2>/dev/null; then
    echo "  [SUCCESS] Local port $PORT is OPEN and accepting connections."
else
    echo "  [INFO] Port $PORT is not active or server is not currently running."
fi

echo ""
echo "Testing external Internet reachability (air-gap test)..."
if ping -c 1 -W 1 1.1.1.1 &> /dev/null; then
    echo "  [NOTE] External Internet connection is ACTIVE (Hybrid mode)."
else
    echo "  [SUCCESS] Air-gapped offline environment confirmed."
fi

echo "=========================================================="
