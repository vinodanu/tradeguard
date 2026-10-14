package com.tradeguard.trade;

import static org.junit.jupiter.api.Assertions.*;

import com.tradeguard.*;
import com.tradeguard.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;

class TradeWorkflowTest {
  static class Store extends SqlStore {
    int writes;
    Map<String, Object> previous;
    OrderRow order;

    Store() {
      super(null);
    }

    @Override
    public Map<String, Object> previous(String scope, String key, Object request) {
      return previous;
    }

    @Override
    public <T extends SqlRow> T lock(Class<T> type, String id) {
      return type.cast(order);
    }

    @Override
    public <T extends SqlRow> T insert(Class<T> type, String id, Map<String, Object> data) {
      writes++;
      throw new AssertionError("Unexpected write");
    }

    @Override
    public void emit(Event e) {
      writes++;
      throw new AssertionError("Unexpected event");
    }
  }

  Store store = new Store();
  TradeApi api =
      new TradeApi(
          store,
          (id, token) -> {
            throw new Problem(503, "Compliance unavailable");
          });

  Jwt user(String role) {
    return Jwt.withTokenValue("test")
        .header("alg", "RS256")
        .subject("trader")
        .claim("role", role)
        .claim("portfolios", List.of("PORTFOLIO_A"))
        .build();
  }

  Map<String, Object> request() {
    return Map.of(
        "portfolioId",
        "PORTFOLIO_A",
        "instrumentId",
        "ACME",
        "side",
        "BUY",
        "quantity",
        "1",
        "price",
        "10");
  }

  @Test
  void viewerCannotSubmit() {
    assertThrows(Problem.class, () -> api.submit(user("VIEWER"), "request-key", request()));
    assertEquals(0, store.writes);
  }

  @Test
  void replayReturnsOriginalWithoutPublishingAgain() {
    store.previous = Map.of("id", "existing");
    assertEquals(
        "existing", api.submit(user("TRADER"), "request-key", request()).getBody().get("id"));
    assertEquals(0, store.writes);
  }

  @Test
  void blockedOrderCannotExecute() {
    store.order = new OrderRow();
    store.order.id = "order";
    store.order.version = 1L;
    store.order.data = Json.write(Map.of("portfolioId", "PORTFOLIO_A", "status", "BLOCKED"));
    var e =
        assertThrows(
            Problem.class,
            () ->
                api.execute(user("TRADER"), "order", "execute-key", Map.of("expectedVersion", 1)));
    assertEquals(409, e.status);
    assertEquals(0, store.writes);
  }

  @Test
  void inaccessibleOrderCannotExecute() {
    store.order = new OrderRow();
    store.order.id = "order";
    store.order.version = 1L;
    store.order.data = Json.write(Map.of("portfolioId", "PORTFOLIO_B", "status", "READY"));
    var e =
        assertThrows(
            Problem.class,
            () ->
                api.execute(user("TRADER"), "order", "execute-key", Map.of("expectedVersion", 1)));
    assertEquals(403, e.status);
    assertEquals(0, store.writes);
  }

  @Test
  void dependencyFailureCannotExecute() {
    store.order = new OrderRow();
    store.order.id = "order";
    store.order.version = 1L;
    store.order.data =
        Json.write(
            Map.of("portfolioId", "PORTFOLIO_A", "status", "READY", "evaluationId", "evaluation"));
    var error =
        assertThrows(
            Problem.class,
            () ->
                api.execute(user("TRADER"), "order", "execute-key", Map.of("expectedVersion", 1)));
    assertEquals(503, error.status);
    assertEquals(0, store.writes);
  }
}
