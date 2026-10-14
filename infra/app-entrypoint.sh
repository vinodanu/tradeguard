#!/bin/bash
set -euo pipefail
if [ -n "${JWT_PRIVATE_KEY:-}" ]; then
  install -m 400 -o app -g app "$JWT_PRIVATE_KEY" /tmp/tradeguard-private.pem
  export JWT_PRIVATE_KEY=/tmp/tradeguard-private.pem
fi
exec gosu app java -XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError -jar /app/app.jar
