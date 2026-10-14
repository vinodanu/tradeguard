package com.tradeguard.compliance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;

class RuleEngineTest {
  Map<String, Object> input(String instrument, String quantity, String price) {
    return Map.of("instrumentId", instrument, "quantity", quantity, "price", price);
  }

  Map<String, Object> rules() {
    return Map.of(
        "restrictedInstruments",
        List.of("BANNED"),
        "restrictedIssuers",
        List.of("BAD_ISSUER"),
        "maxNotional",
        "100000");
  }

  Map<String, Object> node(String issuer, String... children) {
    return Map.of("issuer", issuer, "underlyings", List.of(children));
  }

  Map<String, Object> dataset(Map<String, Object> nodes) {
    return Map.of("instruments", nodes);
  }

  @Test
  void normalOrderAllowed() {
    var r =
        RuleEngine.evaluate(
            input("GOOD", "10", "100"), rules(), dataset(Map.of("GOOD", node("GOOD_ISSUER"))));
    assertEquals("ALLOW", r.outcome());
  }

  @Test
  void directRestrictionBlocks() {
    assertEquals(
        "BLOCK",
        RuleEngine.evaluate(
                input("BANNED", "1", "1"), rules(), dataset(Map.of("BANNED", node("OK"))))
            .outcome());
  }

  @Test
  void indirectRestrictionPreservesPath() {
    var r =
        RuleEngine.evaluate(
            input("FUND", "1", "1"),
            rules(),
            dataset(Map.of("FUND", node("OK", "BANNED"), "BANNED", node("OK"))));
    assertEquals("BLOCK", r.outcome());
    assertEquals(List.of("FUND", "BANNED"), r.reasons().getFirst().get("path"));
  }

  @Test
  void issuerRestrictionBlocks() {
    assertEquals(
        "BLOCK",
        RuleEngine.evaluate(
                input("GOOD", "1", "1"), rules(), dataset(Map.of("GOOD", node("BAD_ISSUER"))))
            .outcome());
  }

  @Test
  void exactThresholdAllowed() {
    assertEquals(
        "ALLOW",
        RuleEngine.evaluate(
                input("GOOD", "1000", "100"), rules(), dataset(Map.of("GOOD", node("OK"))))
            .outcome());
  }

  @Test
  void decimalAboveThresholdRequiresReview() {
    assertEquals(
        "REVIEW",
        RuleEngine.evaluate(
                input("GOOD", "1000.000001", "100"), rules(), dataset(Map.of("GOOD", node("OK"))))
            .outcome());
  }

  @Test
  void blockTakesPrecedenceOverReview() {
    var r =
        RuleEngine.evaluate(
            input("BANNED", "2000", "100"), rules(), dataset(Map.of("BANNED", node("OK"))));
    assertEquals("BLOCK", r.outcome());
    assertEquals(2, r.reasons().size());
  }

  @Test
  void missingInstrumentRequiresReview() {
    assertEquals(
        "REVIEW",
        RuleEngine.evaluate(input("UNKNOWN", "1", "1"), rules(), dataset(Map.of())).outcome());
  }

  @Test
  void cycleRequiresReview() {
    var r =
        RuleEngine.evaluate(
            input("A", "1", "1"),
            rules(),
            dataset(Map.of("A", node("OK", "B"), "B", node("OK", "A"))));
    assertEquals("REVIEW", r.outcome());
    assertTrue(r.reasons().stream().anyMatch(d -> d.get("code").equals("RELATIONSHIP_CYCLE")));
  }

  @Test
  void depthExceededRequiresReview() {
    var nodes = new HashMap<String, Object>();
    for (int i = 0; i < 7; i++) nodes.put("N" + i, node("OK", "N" + (i + 1)));
    var r = RuleEngine.evaluate(input("N0", "1", "1"), rules(), dataset(nodes));
    assertTrue(r.reasons().stream().anyMatch(d -> d.get("code").equals("MAX_DEPTH")));
  }

  @Test
  void diamondRelationshipIsNotACycle() {
    var r =
        RuleEngine.evaluate(
            input("ROOT", "1", "1"),
            rules(),
            dataset(
                Map.of(
                    "ROOT",
                    node("OK", "A", "B"),
                    "A",
                    node("OK", "C"),
                    "B",
                    node("OK", "C"),
                    "C",
                    node("OK"))));
    assertEquals("ALLOW", r.outcome());
  }

  @Test
  void missingIssuerRequiresReview() {
    assertEquals(
        "REVIEW",
        RuleEngine.evaluate(input("A", "1", "1"), rules(), dataset(Map.of("A", node(""))))
            .outcome());
  }

  @Test
  void rulesValidateIdentifiersAndThreshold() {
    assertDoesNotThrow(
        () ->
            ComplianceApi.validateRule(
                Map.of(
                    "name",
                    "Valid rules",
                    "restrictedInstruments",
                    List.of("ABC"),
                    "restrictedIssuers",
                    List.of(),
                    "maxNotional",
                    "10")));
    assertThrows(
        com.tradeguard.Problem.class,
        () ->
            ComplianceApi.validateRule(
                Map.of(
                    "name",
                    "Valid rules",
                    "restrictedInstruments",
                    List.of("bad space"),
                    "restrictedIssuers",
                    List.of(),
                    "maxNotional",
                    "10")));
  }
}
