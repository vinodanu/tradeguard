# Local operations and recovery

## Inspect health

```sh
docker compose ps
docker compose logs --tail=100 trade-service compliance-service investigation-service live-view-service
docker stats --no-stream
```

The default healthchecks check service sockets after startup. Spring Actuator health endpoints are available on the private service network; detailed diagnostics are not publicly exposed. Logs rotate for backend applications. Correlation and event IDs are stored on events and audits. Consumer lag is available through Kafka's consumer-group command.

```sh
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --all-groups --describe
```

## Broker outage

Stop Kafka, submit a new order, and observe that it remains evaluating. Restart Kafka and wait for the outbox to publish:

```sh
docker compose stop kafka
# Submit an order in the UI.
docker compose start kafka
```

The acceptance script automates this scenario. Do not take down shared or unrelated infrastructure.

## Consumers and failed events

```sh
docker compose restart compliance-service investigation-service live-view-service
```

There are three processing retries with a one-second backoff. Unrecoverable events are sent to the matching DLT. Admin Demo operations shows failed records and queues original-event replay. Invalid schema events cannot be replayed until the producer has been corrected. Already processed IDs are ignored.

## Rebuild dashboard

Use **admin → Demo operations → Rebuild dashboard projections**. The service creates fresh collections and atomically changes the active generation. The durable feed is not trimmed in v1; it is the rebuild source. Archive old view generations only after validation. Rebuild is designed for the single-consumer demo profile.

## Backup and restore

These commands are for the local MongoDB profile, not Atlas. The tool briefly stops application services and Kafka, dumps MySQL/MongoDB, snapshots Kafka including offsets, writes checksums, and restarts the services.

```sh
python scripts/backup.py backup
python scripts/backup.py restore --directory backups/EXACT_BACKUP_DIRECTORY
```

Restore replaces this application's demo databases and Kafka volume. Retain the original `.env` and signing keys separately; the tool deliberately does not export secrets. Snapshot consistency depends on all writes originating from the stopped application services. Atlas backup needs a separate owner-controlled `mongodump` workflow.

## Performance measurement

For a local test exceeding the 500-order demo cap:

```sh
docker compose -f compose.yaml -f compose.performance.yaml up -d
python scripts/performance.py --seconds 1800 --rate 5 --clients 20
```

The script writes measured latency, errors, workload, host details, and end-of-run memory to `docs/performance-results.json`. Observe `docker stats` during the run to capture peak memory. The cap increase is local-only; never combine it with the AWS/Atlas profile. API polling adds up to 250 ms to observed submission-to-decision latency. Reset the demo dataset after a high-volume run.

## Teardown

`docker compose down` preserves data. `docker compose down -v` removes this demo's volumes. For AWS, use `terraform destroy` and verify there are no retained volumes, snapshots, or public IP allocations. DNS and Atlas resources are separately managed.
