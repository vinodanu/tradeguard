#!/bin/bash
set -euo pipefail
dnf install -y docker git python3-pip
systemctl enable --now docker
mkdir -p /usr/local/lib/docker/cli-plugins
curl --fail --location https://github.com/docker/compose/releases/download/v2.40.3/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
# Owner initializes /opt/tradeguard with repository, secrets, Atlas URI, and HTTPS.
# No application secrets, AWS access keys, or paid-plan upgrades are embedded in user data.

# Automatically power off this instance at the declared deadline. The owner must
# also terraform destroy: stopped-instance storage can continue consuming credits.
cat >/etc/systemd/system/tradeguard-deadline.service <<'SERVICE'
[Unit]
Description=Stop TradeGuard at the demo deadline
[Service]
Type=oneshot
ExecStart=/usr/sbin/shutdown -h now
SERVICE
cat >/etc/systemd/system/tradeguard-deadline.timer <<'TIMER'
[Unit]
Description=TradeGuard scheduled demo shutdown
[Timer]
OnCalendar=${teardown_at}
Persistent=true
[Install]
WantedBy=timers.target
TIMER
systemctl daemon-reload
systemctl enable --now tradeguard-deadline.timer
