package com.tradeguard.investigation;

import static org.junit.jupiter.api.Assertions.*;

import com.tradeguard.*;
import com.tradeguard.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CaseWorkflowTest {
  static class Store extends SqlStore {
    CaseRow row;
    int writes;

    Store() {
      super(null);
    }

    @Override
    public Map<String, Object> previous(String scope, String key, Object request) {
      return null;
    }

    @Override
    public <T extends SqlRow> T lock(Class<T> type, String id) {
      return type.cast(row);
    }

    @Override
    public void update(SqlRow r, Map<String, Object> data) {
      writes++;
      r.data = Json.write(data);
    }
  }

  Store store = new Store();
  CaseApi api = new CaseApi(store);

  Jwt user(String name, String role) {
    return Jwt.withTokenValue("test")
        .header("alg", "RS256")
        .subject(name)
        .claim("role", role)
        .claim("portfolios", List.of("PORTFOLIO_A"))
        .build();
  }

  void seed(String trader) {
    store.row = new CaseRow();
    store.row.id = "case";
    store.row.version = 1L;
    store.row.data =
        Json.write(
            Map.of(
                "workflow",
                "PRE_TRADE",
                "portfolioId",
                "PORTFOLIO_A",
                "traderId",
                trader,
                "assignedTo",
                "reviewer",
                "status",
                "IN_PROGRESS"));
  }

  Map<String, Object> resolution(long version) {
    return Map.of(
        "expectedVersion",
        version,
        "disposition",
        "APPROVED",
        "note",
        "Synthetic review explanation");
  }

  @Test
  void traderCannotResolve() {
    assertThrows(
        Problem.class,
        () -> api.resolve(user("trader", "TRADER"), "case", "request-key", resolution(1)));
    assertEquals(0, store.writes);
  }

  @Test
  void selfApprovalIsRejectedEvenForAnAdmin() {
    seed("admin");
    var error =
        assertThrows(
            Problem.class,
            () -> api.resolve(user("admin", "ADMIN"), "case", "request-key", resolution(1)));
    assertEquals(403, error.status);
    assertEquals(0, store.writes);
  }

  @Test
  void concurrentStaleReviewerActionIsRejected() {
    seed("trader");
    var error =
        assertThrows(
            Problem.class,
            () -> api.resolve(user("reviewer", "REVIEWER"), "case", "request-key", resolution(0)));
    assertEquals(409, error.status);
    assertEquals(0, store.writes);
  }

  @Test
  void reviewerMustOwnTheAssignment() {
    seed("trader");
    var error =
        assertThrows(
            Problem.class,
            () ->
                api.resolve(
                    user("another-reviewer", "REVIEWER"), "case", "request-key", resolution(1)));
    assertEquals(409, error.status);
    assertEquals(0, store.writes);
  }

  @Test
  void noteIsRequired() {
    assertThrows(
        Problem.class,
        () ->
            api.resolve(
                user("reviewer", "REVIEWER"),
                "case",
                "request-key",
                Map.of("expectedVersion", 1, "disposition", "APPROVED", "note", "")));
    assertEquals(0, store.writes);
  }
}
