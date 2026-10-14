#!/bin/bash
# Run only on the gated EC2 host, after adding Atlas URIs and DNS settings to .env.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
[[ "$DUCKDNS_SUBDOMAIN" =~ ^[a-z0-9-]{1,63}$ ]]
: "${DUCKDNS_TOKEN:?Required}" "${TLS_EMAIL:?Required}" "${ATLAS_COMPLIANCE_URI:?Required}" "${ATLAS_LIVE_URI:?Required}"
mkdir -p secrets/acme secrets/tls
chmod 700 secrets
# DNS API result is checked without printing the token.
python3 scripts/update-dns.py
docker run -d --name tradeguard-acme -p 80:80 -v "$PWD/secrets/acme:/usr/share/nginx/html:ro" nginx:1.28.0-alpine
trap 'docker rm -f tradeguard-acme >/dev/null 2>&1 || true' EXIT
docker run --rm -v "$PWD/secrets/tls:/etc/letsencrypt" -v "$PWD/secrets/acme:/var/www/acme" certbot/certbot:v5.1.0 certonly --webroot -w /var/www/acme --non-interactive --agree-tos --email "$TLS_EMAIL" --cert-name tradeguard -d "$DUCKDNS_SUBDOMAIN.duckdns.org"
docker rm -f tradeguard-acme >/dev/null
trap - EXIT
docker compose -f compose.yaml -f compose.aws.yaml up -d --no-build --wait --wait-timeout 300
cat >/etc/systemd/system/tradeguard-tls.service <<'SERVICE'
[Service]
Type=oneshot
WorkingDirectory=/opt/tradeguard
ExecStart=/bin/bash /opt/tradeguard/scripts/renew-tls.sh
SERVICE
cat >/etc/systemd/system/tradeguard-tls.timer <<'TIMER'
[Timer]
OnCalendar=daily
Persistent=true
[Install]
WantedBy=timers.target
TIMER
cat >/etc/systemd/system/tradeguard-dns.service <<'SERVICE'
[Service]
Type=oneshot
WorkingDirectory=/opt/tradeguard
ExecStart=/usr/bin/python3 /opt/tradeguard/scripts/update-dns.py
SERVICE
cat >/etc/systemd/system/tradeguard-dns.timer <<'TIMER'
[Timer]
OnBootSec=60
OnUnitActiveSec=300
[Install]
WantedBy=timers.target
TIMER
systemctl daemon-reload
systemctl enable --now tradeguard-tls.timer tradeguard-dns.timer
