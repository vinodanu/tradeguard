package com.tradeguard;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;

class AccessTest {
  Jwt jwt(String role, List<String> portfolios) {
    return Jwt.withTokenValue("test")
        .header("alg", "RS256")
        .subject("trader")
        .issuer("tradeguard")
        .audience(List.of("tradeguard-api"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .claim("role", role)
        .claim("portfolios", portfolios)
        .build();
  }

  @Test
  void viewerCannotMutate() {
    assertThrows(Problem.class, () -> Access.roles(jwt("VIEWER", List.of("A")), "TRADER", "ADMIN"));
  }

  @Test
  void portfolioIsolation() {
    assertThrows(Problem.class, () -> Access.portfolio(jwt("TRADER", List.of("A")), "B"));
  }

  @Test
  void correctPortfolioAllowed() {
    assertDoesNotThrow(() -> Access.portfolio(jwt("TRADER", List.of("A")), "A"));
  }

  @Test
  void staleExpectedVersionRejected() {
    assertThrows(Problem.class, () -> Access.expected(Map.of("expectedVersion", 1), 2));
  }

  @Test
  void missingVersionRejected() {
    assertThrows(Problem.class, () -> Access.expected(Map.of(), 2));
  }

  @Test
  void invalidAudienceRejected() {
    var j =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .issuer("tradeguard")
            .audience(List.of("wrong"))
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    assertTrue(Keys.validator().validate(j).hasErrors());
  }

  @Test
  void invalidIssuerRejected() {
    var j =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .issuer("wrong")
            .audience(List.of("tradeguard-api"))
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    assertTrue(Keys.validator().validate(j).hasErrors());
  }

  @Test
  void eventRoundTripAndTopic() {
    var e =
        Event.create(
            "OrderSubmitted", "order", 1, "correlation", null, Map.of("quantity", "0.000001"));
    assertEquals(e, Json.read(Json.write(e), Event.class));
    assertEquals("tradeguard.trade.v1", e.topic());
  }

  @Test
  void canonicalHashIgnoresMapOrder() {
    assertEquals(
        Json.hash(new LinkedHashMap<>(Map.of("a", 1, "b", 2))),
        Json.hash(new TreeMap<>(Map.of("b", 2, "a", 1))));
  }
}
