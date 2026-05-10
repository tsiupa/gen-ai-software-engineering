#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "Building banking-api..."
mvn -f "$PROJECT_DIR/pom.xml" package -DskipTests -q

echo "Starting banking-api on http://localhost:8080 ..."
java -jar "$PROJECT_DIR/target/banking-api-1.0.0.jar"
