#!/usr/bin/env bash
# GhostNet Startup Script
# YOUR NETWORK. YOUR PEOPLE. YOUR RULES.

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=========================================================="
echo " Starting GhostNet Platform..."
echo "=========================================================="

if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH. Please install OpenJDK 21 LTS."
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d. -f1)
echo "Detected Java version: $JAVA_VER"

JAR_FILE="target/ghostnet-1.0.0.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Building GhostNet executable JAR package..."
    ./mvnw clean package -DskipTests
fi

echo "Launching GhostNet Server..."
exec java -jar "$JAR_FILE" "$@"
