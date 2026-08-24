#!/usr/bin/env bash
# Starts backend (Spring Boot) and frontend (Vite) together for local dev.
# Ctrl+C stops both.
set -euo pipefail
cd "$(dirname "$0")"

trap 'kill 0' EXIT

(cd frontend && npm run dev) &
./mvnw spring-boot:run &

wait
