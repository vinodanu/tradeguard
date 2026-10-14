# Architecture decisions

## ADR 1: separate transactional and reactive execution models

Trade and Investigation use MVC/JPA for MySQL transactions. Compliance and Live View use WebFlux/reactive MongoDB. Kafka listeners run on dedicated worker threads and await reactive persistence before acknowledgment. HTTP event loops never perform JPA work. The Trade Service uses a bounded blocking HTTP freshness check on its MVC worker before execution.

Shared modules contain event serialization, authentication utilities, problem types, and persistence infrastructure. Domain entity classes remain service-owned. SQL aggregates have JSON payload columns, primary keys, `@Version` fields, and generated-column indexes. These are an explicit portfolio simplification; future financial ledgers should introduce normalized line items and stronger database-level domain constraints.

## ADR 2: outboxes, pinned snapshots, and idempotency

SQL writes commit business aggregates and outbox entries in one transaction. Compliance persists a pending evaluation snapshot before computing the result; results, inbox, and outbox then commit in a MongoDB transaction. A restart reuses that snapshot. Acknowledgments follow durable processing. Producer acknowledgment precedes marking an outbox published; a crash in between can publish duplicates, which inboxes and business identities suppress.

Unique business identifiers protect one execution per order, one imported source/external-trade identifier, and one case per evaluation. Expected versions and order row locks guard concurrent mutations. Consumers reject obsolete attempts, and projections reject obsolete aggregate versions. Publisher order is not assumed; aggregate versions protect view state.

Versioned rulesets and instrument datasets are immutable. One configuration pointer selects a revision pair. Historical checks choose the most recent effective configuration at or before execution time. Seed configuration begins at 2000-01-01 only to support synthetic fixtures; older trades get a data-quality result.

Approval belongs to a specific evaluation. Publishing new rules forces reevaluation at execution and invalidates prior approval. A block cannot be overridden. The freshness check is a sampled configuration check, not an atomic transaction with a market venue.

## ADR 3: budget-aware demonstration hosting

Docker Compose preserves the same service boundaries locally and on one EC2 host. No managed Kafka, Kubernetes, RDS, load balancer, or NAT gateway is needed. Atlas Free holds the MongoDB databases in the AWS profile. Default traffic is capped at 500 orders and 1000 executions to protect the demonstration footprint.

Costs include storage, IPv4, transfer, and monitoring alongside compute. An application budget alert is not a hard stop. The owner verifies billing before provisioning, schedules shutdown before expiry, and destroys all resources afterward. AWS hosting is temporary and the local recording remains the persistent portfolio artifact.

## Live views and maintenance

Each dashboard notification is persisted with a sequence before its consumer offset commits. SSE replays later notifications and always requests a snapshot refresh on reconnect. Access is filtered by portfolio. Stream sessions end when their JWT expires. Dashboard reads are eventually consistent; mutation screens fetch authoritative records.

Projection rebuilds create fresh collections from the full durable feed, then switch a pointer atomically. A local read/write gate pauses the single Live View consumer while rebuilding. This maintenance mechanism assumes one Live View instance. Old generations remain available for inspection and can be archived manually. Do not deploy multiple instances without replacing the gate with a distributed coordination mechanism.

The schema is additive within v1. Breaking event changes require a new topic version. Flyway migrations are forward-only; image rollback must remain compatible with applied migrations.
