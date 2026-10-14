#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker run --rm -v "$PWD/secrets/tls:/etc/letsencrypt" -v "$PWD/secrets/acme:/var/www/acme" certbot/certbot:v5.1.0 renew --quiet
docker compose -f compose.yaml -f compose.aws.yaml exec -T web nginx -s reload
