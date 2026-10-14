package com.tradeguard.trade;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import com.tradeguard.*;
import com.tradeguard.sql.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Transactional
public class TradeApi {
  private final SqlStore store;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final FreshnessVerifier compliance;

  public TradeApi(SqlStore store, FreshnessVerifier compliance) {
    this.store = store;
    this.compliance = compliance;
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(@RequestBody Map<String, Object> b) {
    String username = Json.str(b, "username");
    var user = store.optional(UserRow.class, Json.id("user:" + username));
    Problem.require(user != null, 401, "Invalid credentials");
    var data = Json.map(user.data);
    Problem.require(
        passwords.matches(Json.str(b, "password"), Json.str(data, "passwordHash")),
        401,
        "Invalid credentials");
    try {
      var claims =
          new JWTClaimsSet.Builder()
              .issuer("tradeguard")
              .audience("tradeguard-api")
              .subject(username)
              .issueTime(new Date())
              .expirationTime(Date.from(Instant.now().plusSeconds(900)))
              .claim("role", data.get("role"))
              .claim("portfolios", data.get("portfolios"))
              .build();
      var jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("tradeguard-v1").build(), claims);
      jwt.sign(new RSASSASigner(Keys.privateKey()));
      return Map.of(
          "accessToken",
          jwt.serialize(),
          "expiresIn",
          900,
          "user",
          username,
          "role",
          data.get("role"));
    } catch (Exception e) {
      throw new IllegalStateException("Could not issue token", e);
    }
  }

  @GetMapping("/portfolios")
  public List<Map<String, Object>> portfolios(@AuthenticationPrincipal Jwt j) {
    return store.list(PortfolioRow.class).stream()
        .filter(p -> j.getClaimAsStringList("portfolios").contains(p.get("id")))
        .toList();
  }

  @PostMapping("/orders")
  public ResponseEntity<Map<String, Object>> submit(
      @AuthenticationPrincipal Jwt j,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @RequestBody Map<String, Object> b) {
    Access.roles(j, "TRADER", "ADMIN");
    Map<String, Object> input = validated(b);
    Access.portfolio(j, Json.str(input, "portfolioId"));
    String scope = "submit:" + j.getSubject();
    var old = store.previous(scope, key, input);
    if (old != null) return ResponseEntity.accepted().body(old);
    Problem.require(
        store.count(OrderRow.class)
            < Integer.parseInt(System.getenv().getOrDefault("MAX_DEMO_ORDERS", "500")),
        409,
        "Demo limit reached (500 orders); archive/reset the local dataset");
    String id = UUID.randomUUID().toString();
    input.put("traderId", j.getSubject());
    input.put("status", "EVALUATING");
    input.put("createdAt", Instant.now().toString());
    input.put("attempt", 1);
    input.put("evaluationId", "");
    var row = store.insert(OrderRow.class, id, input);
    emitSubmitted(row, input, null);
    audit(id, "SUBMITTED", j.getSubject(), input);
    var result = store.view(row);
    store.remember(scope, key, validated(b), result);
    return ResponseEntity.accepted().body(result);
  }

  @GetMapping("/orders/{id}")
  public Map<String, Object> order(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    var row = store.find(OrderRow.class, id);
    var d = store.view(row);
    Access.portfolio(j, Json.str(d, "portfolioId"));
    var result = Json.copy(d);
    result.put(
        "audit",
        store.list(AuditRow.class).stream()
            .filter(a -> id.equals(a.get("orderId")))
            .sorted(Comparator.comparing(a -> Json.str(a, "at")))
            .toList());
    return result;
  }

  @PostMapping("/orders/{id}/execute")
  public ResponseEntity<Map<String, Object>> execute(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @RequestBody Map<String, Object> b) {
    Access.roles(j, "TRADER", "ADMIN");
    String scope = "execute:" + j.getSubject() + ":" + id;
    var old = store.previous(scope, key, b);
    if (old != null)
      return ResponseEntity.status(Json.number(old, "httpStatus") == 202 ? 202 : 200).body(old);
    var row = store.lock(OrderRow.class, id);
    var d = store.view(row);
    Access.portfolio(j, Json.str(d, "portfolioId"));
    Access.expected(b, row.version);
    Problem.require("READY".equals(d.get("status")), 409, "Order is not ready for execution");
    Map<String, Object> freshness =
        compliance.check(Json.str(d, "evaluationId"), j.getTokenValue());
    if (!Boolean.TRUE.equals(freshness.get("current"))) {
      d.put("status", "EVALUATING");
      d.put("attempt", Json.number(d, "attempt") + 1);
      d.put("evaluationId", "");
      d.remove("approvedBy");
      store.update(row, d);
      emitSubmitted(row, d, Json.str(freshness, "evaluationId"));
      audit(id, "REEVALUATION_REQUIRED", j.getSubject(), freshness);
      var result = store.view(row);
      result.put("httpStatus", 202);
      store.remember(scope, key, b, result);
      return ResponseEntity.accepted().body(result);
    }
    String executionId = Json.id("execution:" + id);
    Instant now = Instant.now();
    var execution = Json.copy(d);
    execution.remove("version");
    execution.put("orderId", id);
    execution.put("executionId", executionId);
    execution.put("executedAt", now.toString());
    execution.put("externalKey", "simulated:" + id);
    store.insert(ExecutionRow.class, executionId, execution);
    d.put("status", "EXECUTED");
    d.put("executionId", executionId);
    store.update(row, d);
    store.emit(Event.create("TradeExecuted", id, row.version, id, null, execution));
    emitChanged(row);
    audit(id, "EXECUTED", j.getSubject(), Map.of("executionId", executionId));
    var result = store.view(row);
    result.put("httpStatus", 200);
    store.remember(scope, key, b, result);
    return ResponseEntity.ok(result);
  }

  @PostMapping("/executions/import")
  public ResponseEntity<Map<String, Object>> importTrades(
      @AuthenticationPrincipal Jwt j,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @RequestBody Map<String, Object> b) {
    Access.roles(j, "ADMIN");
    String csv = Json.str(b, "csv");
    Problem.require(csv.length() <= 100000, 400, "CSV is too large");
    String scope = "import:" + j.getSubject();
    var old = store.previous(scope, key, b);
    if (old != null) return ResponseEntity.accepted().body(old);
    var rows = Csv.validate(csv);
    Problem.require(
        store.count(ExecutionRow.class) + rows.size() <= 1000,
        409,
        "Demo execution limit reached (1000)");
    List<String> ids = new ArrayList<>();
    for (var item : rows) {
      Access.portfolio(j, Json.str(item, "portfolioId"));
      String id = Json.id("import:" + item.get("source") + ":" + item.get("externalTradeId"));
      var existing = store.optional(ExecutionRow.class, id);
      String fingerprint = Json.hash(item);
      if (existing != null) {
        Problem.require(
            fingerprint.equals(Json.str(Json.map(existing.data), "importHash")),
            409,
            "External trade identifier already exists with different input");
        ids.add(id);
        continue;
      }
      var d = Json.copy(item);
      d.put("executionId", id);
      d.put("traderId", j.getSubject());
      d.put("externalKey", item.get("source") + ":" + item.get("externalTradeId"));
      d.put("importHash", fingerprint);
      d.put("status", "EXECUTED");
      store.insert(ExecutionRow.class, id, d);
      store.emit(Event.create("TradeExecuted", id, 0, id, null, d));
      ids.add(id);
    }
    var result = Map.<String, Object>of("executionIds", ids, "accepted", ids.size());
    store.remember(scope, key, b, result);
    return ResponseEntity.accepted().body(result);
  }

  @KafkaListener(topics = {"tradeguard.compliance.v1", "tradeguard.case.v1"})
  public void events(String raw) {
    Event e = Json.read(raw, Event.class);
    e.validate();
    if (store.seen(e.eventId())) return;
    var p = e.payload();
    if (e.eventType().equals("PreTradeEvaluated")) {
      var row = store.lock(OrderRow.class, Json.str(p, "orderId"));
      var d = store.view(row);
      if (Json.number(p, "attempt") == Json.number(d, "attempt")
          && "EVALUATING".equals(d.get("status"))) {
        String status =
            switch (Json.str(p, "outcome")) {
              case "ALLOW" -> "READY";
              case "BLOCK" -> "BLOCKED";
              default -> "AWAITING_REVIEW";
            };
        d.put("status", status);
        d.put("evaluationId", p.get("evaluationId"));
        d.put("outcome", p.get("outcome"));
        store.update(row, d);
        emitChanged(row);
        audit(row.id, "EVALUATED", "system", p);
      }
    }
    if (e.eventType().equals("CaseResolved") && "PRE_TRADE".equals(p.get("workflow"))) {
      var row = store.lock(OrderRow.class, Json.str(p, "orderId"));
      var d = store.view(row);
      if (p.get("evaluationId").equals(d.get("evaluationId"))
          && "AWAITING_REVIEW".equals(d.get("status"))) {
        d.put("status", "APPROVED".equals(p.get("disposition")) ? "READY" : "REJECTED");
        d.put("approvedBy", p.get("resolvedBy"));
        store.update(row, d);
        emitChanged(row);
        audit(row.id, "REVIEW_RESOLVED", Json.str(p, "resolvedBy"), p);
      }
    }
    store.inbox(e.eventId());
  }

  private void emitSubmitted(OrderRow row, Map<String, Object> d, String cause) {
    var p = Json.copy(d);
    p.put("orderId", row.id);
    store.emit(
        Event.create(
            "OrderSubmitted", row.id, row.version == null ? 0 : row.version, row.id, cause, p));
  }

  private void emitChanged(OrderRow row) {
    store.emit(Event.create("OrderChanged", row.id, row.version, row.id, null, store.view(row)));
  }

  private void audit(String id, String action, String actor, Map<String, Object> d) {
    store.insert(
        AuditRow.class,
        UUID.randomUUID().toString(),
        Map.of(
            "orderId",
            id,
            "action",
            action,
            "actor",
            actor,
            "at",
            Instant.now().toString(),
            "details",
            d));
  }

  public static Map<String, Object> validated(Map<String, Object> b) {
    Problem.require(
        !b.containsKey("currency") || "USD".equals(b.get("currency")),
        400,
        "Only USD is supported");
    String portfolio = Json.str(b, "portfolioId"),
        instrument = Json.str(b, "instrumentId"),
        side = Json.str(b, "side");
    Problem.require(
        portfolio.length() > 0
            && portfolio.length() <= 64
            && instrument.matches("[A-Z0-9_-]{1,32}"),
        400,
        "Valid portfolioId and instrumentId are required");
    Problem.require(Set.of("BUY", "SELL").contains(side), 400, "side must be BUY or SELL");
    BigDecimal q = decimal(b, "quantity"), p = decimal(b, "price");
    return new LinkedHashMap<>(
        Map.of(
            "portfolioId",
            portfolio,
            "instrumentId",
            instrument,
            "side",
            side,
            "quantity",
            q.toPlainString(),
            "price",
            p.toPlainString(),
            "currency",
            "USD"));
  }

  private static BigDecimal decimal(Map<String, Object> b, String k) {
    Problem.require(b.get(k) instanceof String, 400, k + " must be a decimal string");
    try {
      BigDecimal v = new BigDecimal(Json.str(b, k));
      Problem.require(
          v.signum() > 0 && v.scale() <= 6 && v.precision() <= 18,
          400,
          k + " must be positive, with at most 6 decimal places and 18 digits");
      return v.stripTrailingZeros();
    } catch (NumberFormatException e) {
      throw new Problem(400, k + " is not a decimal");
    }
  }
}
