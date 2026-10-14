package com.tradeguard.live;

import com.tradeguard.*;
import com.tradeguard.mongo.*;
import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.*;

@RestController
@RequestMapping("/api/v1")
public class LiveApi {
  private final MongoStore store;
  private final ProjectionGate gate;

  public LiveApi(MongoStore s, ProjectionGate g) {
    store = s;
    gate = g;
  }

  private Mono<String> collection(String base) {
    return store
        .get("projection_generation", "active")
        .map(d -> Json.str(d, base.equals("order_views") ? "orders" : "cases"));
  }

  private Flux<Map<String, Object>> views(String base) {
    return collection(base).flatMapMany(store::list);
  }

  @GetMapping("/dashboard")
  public Mono<Map<String, Object>> dashboard(@AuthenticationPrincipal Jwt j) {
    return views("order_views")
        .filter(d -> Access.sees(j, d))
        .collectList()
        .zipWith(views("case_views").filter(d -> Access.sees(j, d)).collectList())
        .zipWith(
            store
                .list("feed_events")
                .filter(d -> visible(j, d))
                .sort(Comparator.comparingLong(d -> -Json.number(d, "sequence")))
                .take(20)
                .collectList())
        .map(
            t -> {
              var orders = t.getT1().getT1();
              var cases = t.getT1().getT2();
              var counts = new LinkedHashMap<String, Long>();
              for (String status :
                  List.of(
                      "EVALUATING", "READY", "BLOCKED", "AWAITING_REVIEW", "REJECTED", "EXECUTED"))
                counts.put(
                    status, orders.stream().filter(d -> status.equals(d.get("status"))).count());
              return Map.of(
                  "orders",
                  orders.size(),
                  "statuses",
                  counts,
                  "openCases",
                  cases.stream().filter(c -> !"RESOLVED".equals(c.get("status"))).count(),
                  "recentActivity",
                  t.getT2(),
                  "eventuallyConsistent",
                  true);
            });
  }

  @GetMapping("/orders")
  public Mono<Map<String, Object>> orders(
      @AuthenticationPrincipal Jwt j,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Problem.require(page >= 0 && size >= 1 && size <= 100, 400, "Invalid pagination");
    return views("order_views")
        .filter(d -> Access.sees(j, d))
        .filter(d -> status.isBlank() || status.equals(d.get("status")))
        .filter(d -> q.isBlank() || Json.str(d, "instrumentId").contains(q.toUpperCase()))
        .sort(Comparator.comparing(d -> Json.str(d, "createdAt"), Comparator.reverseOrder()))
        .collectList()
        .map(
            all ->
                Map.of(
                    "items",
                    all.stream().skip((long) page * size).limit(size).toList(),
                    "total",
                    all.size()));
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<String>> stream(
      @AuthenticationPrincipal Jwt j,
      @RequestHeader(value = "Last-Event-ID", defaultValue = "0") String last) {
    long cursor;
    try {
      cursor = Long.parseLong(last);
      Problem.require(cursor >= 0, 400, "Invalid stream cursor");
    } catch (NumberFormatException e) {
      throw new Problem(400, "Invalid stream cursor");
    }
    var position = new java.util.concurrent.atomic.AtomicLong(cursor);
    return Flux.concat(
        Mono.just(ServerSentEvent.<String>builder().event("refresh").data("{}").build()),
        Flux.interval(Duration.ZERO, Duration.ofSeconds(1))
            .concatMap(
                t -> {
                  if (Instant.now().getEpochSecond() >= j.getExpiresAt().getEpochSecond())
                    return Flux.error(new Problem(401, "Access token expired"));
                  return store
                      .db
                      .find(
                          Query.query(Criteria.where("sequence").gt(position.get()))
                              .with(org.springframework.data.domain.Sort.by("sequence"))
                              .limit(100),
                          Document.class,
                          "feed_events")
                      .map(MongoStore::map)
                      .doOnNext(d -> position.set(Json.number(d, "sequence")))
                      .filter(d -> visible(j, d))
                      .map(
                          d ->
                              ServerSentEvent.<String>builder()
                                  .id(Json.str(d, "sequence"))
                                  .event("change")
                                  .data(Json.write(d))
                                  .build())
                      .switchIfEmpty(
                          Mono.just(
                              ServerSentEvent.<String>builder().comment("heartbeat").build()));
                }));
  }

  private boolean visible(Jwt j, Map<String, Object> d) {
    return Json.str(d, "portfolioId").isBlank() || Access.sees(j, d);
  }

  @KafkaListener(topics = {"tradeguard.trade.v1", "tradeguard.compliance.v1", "tradeguard.case.v1"})
  public void events(String raw) {
    Event e = Json.read(raw, Event.class);
    e.validate();
    gate.lock.readLock().lock();
    try {
      apply(e).block(Duration.ofSeconds(30));
    } finally {
      gate.lock.readLock().unlock();
    }
  }

  public Mono<Void> apply(Event e) {
    return collection("order_views")
        .zipWith(collection("case_views"))
        .flatMap(names -> applyTo(e, names.getT1(), names.getT2()));
  }

  private Mono<Void> applyTo(Event e, String orders, String cases) {
    return store
        .seen(e.eventId())
        .flatMap(
            seen -> {
              if (seen) return Mono.empty();
              var p = e.payload();
              Mono<Void> projection = Mono.empty();
              if (Set.of("OrderSubmitted", "OrderChanged").contains(e.eventType())) {
                String id = e.aggregateId();
                projection =
                    store
                        .get(orders, id)
                        .onErrorResume(
                            Problem.class,
                            error ->
                                error.status == 404
                                    ? Mono.just(Map.of("aggregateVersion", -1))
                                    : Mono.error(error))
                        .flatMap(
                            old -> {
                              if (Json.number(old, "aggregateVersion") >= e.aggregateVersion())
                                return Mono.empty();
                              var next = Json.copy(p);
                              next.put("aggregateVersion", e.aggregateVersion());
                              next.put("lastEventAt", e.occurredAt());
                              return store.save(orders, id, next).then();
                            });
              } else if (Set.of("CaseOpened", "CaseChanged", "CaseResolved")
                  .contains(e.eventType())) {
                String id = e.aggregateId();
                projection =
                    store
                        .get(cases, id)
                        .onErrorResume(
                            Problem.class,
                            error ->
                                error.status == 404
                                    ? Mono.just(Map.of("aggregateVersion", -1))
                                    : Mono.error(error))
                        .flatMap(
                            old -> {
                              if (Json.number(old, "aggregateVersion") >= e.aggregateVersion())
                                return Mono.empty();
                              var next = Json.copy(p);
                              next.put("aggregateVersion", e.aggregateVersion());
                              return store.save(cases, id, next).then();
                            });
              }
              Mono<Void> feed =
                  store
                      .db
                      .findAndModify(
                          Query.query(Criteria.where("_id").is("feed")),
                          new Update().inc("value", 1),
                          FindAndModifyOptions.options().returnNew(true).upsert(true),
                          Document.class,
                          "dashboard_counters")
                      .flatMap(
                          counter -> {
                            var f = new LinkedHashMap<String, Object>();
                            f.put("sequence", counter.get("value"));
                            f.put("eventType", e.eventType());
                            f.put("aggregateId", e.aggregateId());
                            f.put("portfolioId", p.getOrDefault("portfolioId", ""));
                            f.put("occurredAt", e.occurredAt());
                            f.put("receivedAt", Instant.now().toString());
                            f.put("event", Json.write(e));
                            return store.insert("feed_events", e.eventId(), f);
                          })
                      .then();
              return store.tx.transactional(projection.then(feed).then(store.inbox(e.eventId())));
            });
  }
}
