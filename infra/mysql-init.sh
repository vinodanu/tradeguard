#!/bin/bash
set -euo pipefail
[[ "$TRADE_DB_PASSWORD" =~ ^[a-f0-9]{48}$ ]] && [[ "$CASE_DB_PASSWORD" =~ ^[a-f0-9]{48}$ ]]
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS trade;
CREATE DATABASE IF NOT EXISTS investigation;
CREATE USER IF NOT EXISTS 'trade'@'%' IDENTIFIED BY '$TRADE_DB_PASSWORD';
CREATE USER IF NOT EXISTS 'investigation'@'%' IDENTIFIED BY '$CASE_DB_PASSWORD';
GRANT ALL PRIVILEGES ON trade.* TO 'trade'@'%';
GRANT ALL PRIVILEGES ON investigation.* TO 'investigation'@'%';
SQL
