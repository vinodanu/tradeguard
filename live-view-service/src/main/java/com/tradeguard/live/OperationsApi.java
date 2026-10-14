package com.tradeguard.live;

import com.tradeguard.*;
import com.tradeguard.mongo.*;
import java.time.*;
import java.util.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsApi {
  private final MongoStore store;
  private final ProjectionGate gate;

  public OperationsApi(MongoStore s, ProjectionGate g) {
    store = s;
    gate = g;
  }

  @KafkaListener(
      topics = {
        "tradeguard.trade.v1.DLT",
        "tradeguard.compliance.v1.DLT",
        "tradeguard.case.v1.DLT"
      },
      groupId = "tradeguard-dead-letter-inspector")
  public void failed(ConsumerRecord<String, String> record) {
    String id = Json.id(record.topic() + ":" + record.partition() + ":" + record.offset());
    store
        .insert(
            "dead_letters",
            id,
            Map.of(
                "topic",
                record.topic(),
                "partition",
                record.partition(),
                "offset",
                record.offset(),
                "raw",
                record.value(),
                "receivedAt",
                Instant.now().toString(),
                "replayCount",
                0))
        .onErrorResume(DuplicateKeyException.class, e -> Mono.empty())
        .block(Duration.ofSeconds(10));
  }

  @GetMapping("/dead-letters")
  public Mono<List<Map<String, Object>>> failures(@AuthenticationPrincipal Jwt j) {
    Access.roles(j, "ADMIN");
    return store.list("dead_letters").take(100).collectList();
  }

  @PostMapping("/dead-letters/{id}/replay")
  public Mono<Map<String, Object>> replay(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @RequestBody Map<String, Object> b) {
    Access.roles(j, "ADMIN");
    Problem.require(
        key != null && key.length() >= 8 && key.length() <= 128,
        400,
        "A valid idempotency key is required");
    String requestId = Json.id("replay:" + j.getSubject() + ":" + id + ":" + key);
    return store
        .seen(requestId)
        .flatMap(
            seen -> {
              if (seen) return Mono.just(Map.of("accepted", true, "duplicate", true));
              return store
                  .get("dead_letters", id)
                  .flatMap(
                      d -> {
                        Event e = Json.read(Json.str(d, "raw"), Event.class);
                        e.validate();
                        d.put("replayCount", Json.number(d, "replayCount") + 1);
                        d.put("replayedBy", j.getSubject());
                        d.put("lastReplayedAt", Instant.now().toString());
                        String outboxId = UUID.randomUUID().toString();
                        return store
                            .tx
                            .transactional(
                                store
                                    .insert(
                                        "outbox",
                                        outboxId,
                                        Map.of("event", Json.write(e), "published", false))
                                    .then(store.save("dead_letters", id, d))
                                    .then(store.inbox(requestId)))
                            .thenReturn(Map.of("accepted", true, "eventId", e.eventId()));
                      });
            });
  }

  @PostMapping("/projections/rebuild")
  public Mono<Map<String, Object>> rebuild(
      @AuthenticationPrincipal Jwt j,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @RequestBody Map<String, Object> b) {
    Access.roles(j, "ADMIN");
    Problem.require(
        key != null && key.length() >= 8 && key.length() <= 128,
        400,
        "A valid idempotency key is required");
    String id = Json.id("rebuild:" + j.getSubject() + ":" + key);
    return Mono.fromCallable(
            () -> {
              gate.lock.writeLock().lock();
              try {
                var previous = store.db.findById(id, org.bson.Document.class, "operations").block();
                if (previous != null) return MongoStore.map(previous);
                String generation = UUID.randomUUID().toString().replace("-", "");
                String orders = "order_views_" + generation, cases = "case_views_" + generation;
                store.db.createCollection(orders).block();
                store.db.createCollection(cases).block();
                var events =
                    store
                        .list("feed_events")
                        .sort(Comparator.comparingLong(d -> Json.number(d, "sequence")))
                        .collectList()
                        .block();
                var orderViews = new HashMap<String, Map<String, Object>>();
                var caseViews = new HashMap<String, Map<String, Object>>();
                for (var f : events) {
                  Event e = Json.read(Json.str(f, "event"), Event.class);
                  Map<String, Map<String, Object>> target =
                      Set.of("OrderSubmitted", "OrderChanged").contains(e.eventType())
                          ? orderViews
                          : Set.of("CaseOpened", "CaseChanged", "CaseResolved")
                                  .contains(e.eventType())
                              ? caseViews
                              : null;
                  if (target == null) continue;
                  var old = target.get(e.aggregateId());
                  if (old != null && Json.number(old, "aggregateVersion") >= e.aggregateVersion())
                    continue;
                  var next = Json.copy(e.payload());
                  next.put("aggregateVersion", e.aggregateVersion());
                  target.put(e.aggregateId(), next);
                }
                for (var entry : orderViews.entrySet())
                  store.insert(orders, entry.getKey(), entry.getValue()).block();
                for (var entry : caseViews.entrySet())
                  store.insert(cases, entry.getKey(), entry.getValue()).block();
                var result =
                    new LinkedHashMap<String, Object>(
                        Map.of(
                            "generation",
                            generation,
                            "orders",
                            orders,
                            "cases",
                            cases,
                            "rebuiltAt",
                            Instant.now().toString(),
                            "rebuiltBy",
                            j.getSubject(),
                            "eventCount",
                            events.size()));
                store
                    .tx
                    .transactional(
                        store
                            .save("projection_generation", "active", result)
                            .then(store.insert("operations", id, result)))
                    .block();
                return result;
              } finally {
                gate.lock.writeLock().unlock();
              }
            })
        .subscribeOn(Schedulers.boundedElastic());
  }
}
