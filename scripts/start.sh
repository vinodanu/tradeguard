#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/bootstrap.py
docker compose up --build -d --wait --wait-timeout 600
echo 'TradeGuard is ready at http://localhost:8080'
