package com.tradeguard.compliance;

import static org.junit.jupiter.api.Assertions.*;

import com.tradeguard.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ProviderFixtureTest {
  @Test
  void serializesActualRuleResultForPactVerification() throws Exception {
    var input = Map.<String, Object>of("instrumentId", "ACME", "quantity", "10", "price", "100");
    var rules =
        Map.<String, Object>of(
            "restrictedInstruments",
            List.of(),
            "restrictedIssuers",
            List.of(),
            "maxNotional",
            "100000");
    var dataset =
        Map.<String, Object>of(
            "instruments", Map.of("ACME", Map.of("issuer", "ACME_CORP", "underlyings", List.of())));
    var result = RuleEngine.evaluate(input, rules, dataset);
    assertEquals("ALLOW", result.outcome());
    var payload = new LinkedHashMap<String, Object>();
    payload.put("orderId", "order-1");
    payload.put("evaluationId", "evaluation-1");
    payload.put("attempt", 1);
    payload.put("portfolioId", "PORTFOLIO_A");
    payload.put("outcome", result.outcome());
    payload.put("reasons", result.reasons());
    Event event =
        ComplianceApi.evaluatedEvent(
            "PRE_TRADE",
            Event.create("OrderSubmitted", "order-1", 1, "order-1", null, input),
            payload);
    Path target =
        Path.of(
                ProviderFixtureTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
            .getParent();
    Files.createDirectories(target);
    Files.writeString(target.resolve("provider-event.json"), Json.write(event));
  }
}
