package com.tradeguard.compliance;

import com.tradeguard.*;
import com.tradeguard.mongo.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.*;

@RestController
@RequestMapping("/api/v1")
public class ComplianceApi {
  private final MongoStore store;

  public ComplianceApi(MongoStore s) {
    store = s;
  }

  @GetMapping("/instruments")
  public Mono<List<Map<String, Object>>> instruments(@RequestParam(defaultValue = "") String q) {
    return store
        .get("active_configuration", "active")
        .flatMap(a -> store.get("instrument_datasets", Json.str(a, "datasetId")))
        .map(
            d -> {
              Map<String, Object> nodes = (Map<String, Object>) d.get("instruments");
              return nodes.entrySet().stream()
                  .filter(e -> e.getKey().contains(q.toUpperCase()))
                  .map(
                      e -> {
                        var m = Json.copy((Map<String, Object>) e.getValue());
                        m.put("id", e.getKey());
                        return m;
                      })
                  .toList();
            });
  }

  @GetMapping("/evaluations/{id}")
  public Mono<Map<String, Object>> evaluation(
      @AuthenticationPrincipal Jwt j, @PathVariable String id) {
    return store
        .get("evaluations", id)
        .map(
            d -> {
              Access.portfolio(j, Json.str(d, "portfolioId"));
              return d;
            });
  }

  @GetMapping("/evaluations/{id}/freshness")
  public Mono<Map<String, Object>> freshness(
      @AuthenticationPrincipal Jwt j, @PathVariable String id) {
    return evaluation(j, id)
        .zipWith(store.get("active_configuration", "active"))
        .map(
            t ->
                Map.of(
                    "evaluationId",
                    id,
                    "current",
                    Objects.equals(t.getT1().get("rulesetId"), t.getT2().get("rulesetId"))
                        && Objects.equals(t.getT1().get("datasetId"), t.getT2().get("datasetId")),
                    "checkedAt",
                    Instant.now().toString()));
  }

  @GetMapping("/rulesets")
  public Mono<Map<String, Object>> rulesets(@AuthenticationPrincipal Jwt j) {
    return store
        .list("rulesets")
        .collectList()
        .zipWith(store.get("active_configuration", "active"))
        .map(t -> Map.of("items", t.getT1(), "active", t.getT2()));
  }

  @PostMapping("/rulesets")
  public Mono<Map<String, Object>> draft(
      @AuthenticationPrincipal Jwt j,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @RequestBody Map<String, Object> b) {
    Access.roles(j, "ADMIN");
    validateRule(b);
    return mutation(
        "draft:" + j.getSubject(),
        key,
        b,
        () -> {
          var d = Json.copy(b);
          d.put("name", Json.str(b, "name"));
          d.put("createdAt", Instant.now().toString());
          d.put("createdBy", j.getSubject());
          d.put("published", false);
          return store.insert("rulesets", UUID.randomUUID().toString(), d);
        });
  }

  @PostMapping("/rulesets/{id}/publish")
  public Mono<Map<String, Object>> publish(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @RequestBody Map<String, Object> b) {
    Access.roles(j, "ADMIN");
    return mutation(
        "publish:" + j.getSubject() + ":" + id,
        key,
        b,
        () ->
            store
                .get("rulesets", id)
                .zipWith(store.get("active_configuration", "active"))
                .flatMap(
                    t -> {
                      var rule = t.getT1();
                      var active = t.getT2();
                      Access.expected(b, Json.number(active, "version"));
                      Problem.require(
                          !Boolean.TRUE.equals(rule.get("published")),
                          409,
                          "Ruleset is immutable after publication");
                      long version = Json.number(active, "version") + 1;
                      var config =
                          new LinkedHashMap<String, Object>(
                              Map.of(
                                  "rulesetId",
                                  id,
                                  "datasetId",
                                  active.get("datasetId"),
                                  "version",
                                  version,
                                  "effectiveAt",
                                  Instant.now().toString()));
                      var query =
                          Query.query(
                              Criteria.where("_id")
                                  .is("active")
                                  .and("version")
                                  .is(Json.number(active, "version")));
                      var update =
                          new Update()
                              .set("rulesetId", id)
                              .set("version", version)
                              .set("effectiveAt", config.get("effectiveAt"));
                      rule.put("published", true);
                      rule.put("publishedAt", config.get("effectiveAt"));
                      return store
                          .db
                          .updateFirst(query, update, "active_configuration")
                          .flatMap(
                              r -> {
                                Problem.require(
                                    r.getModifiedCount() == 1,
                                    409,
                                    "Configuration changed; refresh and retry");
                                return store.save("rulesets", id, rule);
                              })
                          .then(store.insert("configurations", "config-" + version, config))
                          .flatMap(
                              saved ->
                                  store
                                      .emit(
                                          Event.create(
                                              "RulesPublished", id, version, id, null, config))
                                      .thenReturn(saved));
                    }));
  }

  private Mono<Map<String, Object>> mutation(
      String scope,
      String key,
      Object request,
      java.util.function.Supplier<Mono<Map<String, Object>>> work) {
    Problem.require(
        key != null && key.length() >= 8 && key.length() <= 128,
        400,
        "Idempotency-Key must contain 8–128 characters");
    String id = Json.id(scope + ":" + key);
    return store
        .get("idempotency", id)
        .map(
            old -> {
              Problem.require(
                  Json.str(old, "hash").equals(Json.hash(request)),
                  409,
                  "Idempotency key was used for different input");
              return Json.map(Json.str(old, "response"));
            })
        .onErrorResume(
            Problem.class,
            e ->
                e.status == 404
                    ? store.tx.transactional(
                        Mono.defer(work)
                            .flatMap(
                                result ->
                                    store
                                        .insert(
                                            "idempotency",
                                            id,
                                            Map.of(
                                                "hash",
                                                Json.hash(request),
                                                "response",
                                                Json.write(result)))
                                        .thenReturn(result)))
                    : Mono.error(e));
  }

  public static void validateRule(Map<String, Object> b) {
    Problem.require(
        Json.str(b, "name").length() >= 3 && Json.str(b, "name").length() <= 80,
        400,
        "Rule name must be 3–80 characters");
    for (String k : List.of("restrictedInstruments", "restrictedIssuers")) {
      Problem.require(
          b.get(k) instanceof List<?> l && l.size() <= 100,
          400,
          k + " must be an array with at most 100 entries");
      for (Object v : (List<?>) b.get(k))
        Problem.require(
            v instanceof String s && s.matches("[A-Z0-9_-]{1,64}"),
            400,
            "Invalid restriction identifier");
    }
    Problem.require(
        b.get("maxNotional") instanceof String, 400, "maxNotional must be a decimal string");
    try {
      BigDecimal n = new BigDecimal(Json.str(b, "maxNotional"));
      Problem.require(
          n.signum() > 0 && n.scale() <= 6 && n.precision() <= 18,
          400,
          "Invalid notional threshold");
    } catch (NumberFormatException e) {
      throw new Problem(400, "Invalid notional threshold");
    }
  }

  @KafkaListener(topics = "tradeguard.trade.v1")
  public void events(String raw) {
    Event e = Json.read(raw, Event.class);
    e.validate();
    if (!Set.of("OrderSubmitted", "TradeExecuted").contains(e.eventType())) return;
    process(e).block(Duration.ofSeconds(30));
  }

  Mono<Void> process(Event e) {
    return store
        .seen(e.eventId())
        .flatMap(
            seen -> {
              if (seen) return Mono.empty();
              String workflow = e.eventType().equals("OrderSubmitted") ? "PRE_TRADE" : "POST_TRADE";
              String businessId =
                  workflow.equals("PRE_TRADE")
                      ? Json.str(e.payload(), "orderId")
                      : Json.str(e.payload(), "executionId");
              String id =
                  Json.id(
                      "evaluation:"
                          + workflow
                          + ":"
                          + businessId
                          + ":"
                          + (workflow.equals("PRE_TRADE")
                              ? Json.number(e.payload(), "attempt")
                              : 0));
              return snapshot(id, workflow, e)
                  .flatMap(
                      s -> {
                        if (s.get("status").equals("COMPLETE"))
                          return store.tx.transactional(store.inbox(e.eventId()));
                        var input = (Map<String, Object>) s.get("input");
                        var rule = (Map<String, Object>) s.get("rules");
                        var dataset = (Map<String, Object>) s.get("dataset");
                        RuleEngine.Result result =
                            rule.isEmpty()
                                ? new RuleEngine.Result(
                                    "REVIEW",
                                    List.of(
                                        Map.of(
                                            "code",
                                            "MISSING_HISTORICAL_CONFIGURATION",
                                            "severity",
                                            "REVIEW",
                                            "message",
                                            "No configuration covers this execution timestamp")),
                                    List.of())
                                : RuleEngine.evaluate(input, rule, dataset);
                        var d = Json.copy(s);
                        d.put("status", "COMPLETE");
                        d.put("outcome", result.outcome());
                        d.put("reasons", result.reasons());
                        d.put("paths", result.paths());
                        d.put("completedAt", Instant.now().toString());
                        d.put("evaluationId", id);
                        Event output = evaluatedEvent(workflow, e, d);
                        return store.tx.transactional(
                            store
                                .save("evaluations", id, d)
                                .then(store.emit(output))
                                .then(store.inbox(e.eventId())));
                      });
            });
  }

  private Mono<Map<String, Object>> snapshot(String id, String workflow, Event e) {
    return store
        .get("evaluations", id)
        .onErrorResume(
            Problem.class,
            error -> {
              if (error.status != 404) return Mono.error(error);
              Mono<Map<String, Object>> config =
                  workflow.equals("PRE_TRADE")
                      ? store.get("active_configuration", "active")
                      : store
                          .list("configurations")
                          .filter(
                              c ->
                                  !Instant.parse(Json.str(c, "effectiveAt"))
                                      .isAfter(Instant.parse(Json.str(e.payload(), "executedAt"))))
                          .sort(
                              Comparator.comparing(
                                  c -> Instant.parse(Json.str(c, "effectiveAt")),
                                  Comparator.reverseOrder()))
                          .next()
                          .defaultIfEmpty(Map.of());
              return config.flatMap(
                  c -> {
                    Mono<Map<String, Object>> rules =
                        c.isEmpty()
                            ? Mono.just(Map.of())
                            : store.get("rulesets", Json.str(c, "rulesetId"));
                    Mono<Map<String, Object>> dataset =
                        c.isEmpty()
                            ? Mono.just(Map.of())
                            : store.get("instrument_datasets", Json.str(c, "datasetId"));
                    return rules
                        .zipWith(dataset)
                        .flatMap(
                            t -> {
                              var d = new LinkedHashMap<String, Object>();
                              d.put("workflow", workflow);
                              d.put("input", e.payload());
                              d.put("rules", t.getT1());
                              d.put("dataset", t.getT2());
                              d.put("rulesetId", c.getOrDefault("rulesetId", ""));
                              d.put("datasetId", c.getOrDefault("datasetId", ""));
                              d.put("status", "PENDING");
                              d.put("selectedAt", Instant.now().toString());
                              d.put("engineVersion", "1.0.0");
                              d.put("correlationId", e.correlationId());
                              d.put("evaluationAttemptId", id);
                              d.put("portfolioId", e.payload().get("portfolioId"));
                              d.put("traderId", e.payload().get("traderId"));
                              d.put("attempt", e.payload().getOrDefault("attempt", 0));
                              d.put("orderId", e.payload().getOrDefault("orderId", ""));
                              d.put("executionId", e.payload().getOrDefault("executionId", ""));
                              return store
                                  .insert("evaluations", id, d)
                                  .onErrorResume(
                                      DuplicateKeyException.class,
                                      x -> store.get("evaluations", id));
                            });
                  });
            });
  }

  public static Event evaluatedEvent(String workflow, Event source, Map<String, Object> result) {
    return Event.create(
        workflow.equals("PRE_TRADE") ? "PreTradeEvaluated" : "PostTradeEvaluated",
        source.aggregateId(),
        source.aggregateVersion(),
        source.correlationId(),
        source.eventId(),
        result);
  }
}
