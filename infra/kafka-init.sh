#!/bin/bash
set -euo pipefail
for name in trade compliance case; do
 for suffix in "" .DLT; do
 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic "tradeguard.$name.v1$suffix" --partitions 3 --replication-factor 1
 done
done
