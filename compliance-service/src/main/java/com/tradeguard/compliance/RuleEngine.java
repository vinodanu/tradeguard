package com.tradeguard.compliance;

import com.tradeguard.*;
import java.math.*;
import java.util.*;

public final class RuleEngine {
  public record Result(
      String outcome, List<Map<String, Object>> reasons, List<Map<String, Object>> paths) {}

  public static Result evaluate(
      Map<String, Object> input, Map<String, Object> rules, Map<String, Object> dataset) {
    var reasons = new ArrayList<Map<String, Object>>();
    var paths = new ArrayList<Map<String, Object>>();
    Map<String, Object> nodes = (Map<String, Object>) dataset.getOrDefault("instruments", Map.of());
    walk(Json.str(input, "instrumentId"), nodes, rules, new ArrayList<>(), reasons, paths);
    BigDecimal notional =
        new BigDecimal(Json.str(input, "quantity"))
            .multiply(new BigDecimal(Json.str(input, "price")));
    if (notional.compareTo(new BigDecimal(Json.str(rules, "maxNotional"))) > 0)
      reasons.add(
          Map.of(
              "code",
              "MAX_NOTIONAL",
              "severity",
              "REVIEW",
              "message",
              "Order notional "
                  + notional.toPlainString()
                  + " USD exceeds "
                  + rules.get("maxNotional")
                  + " USD",
              "instrumentId",
              Json.str(input, "instrumentId")));
    String outcome =
        reasons.stream().anyMatch(r -> r.get("severity").equals("BLOCK"))
            ? "BLOCK"
            : reasons.isEmpty() ? "ALLOW" : "REVIEW";
    if (reasons.isEmpty())
      reasons.add(
          Map.of(
              "code",
              "NO_RESTRICTIONS",
              "severity",
              "INFO",
              "message",
              "No applicable restrictions matched",
              "instrumentId",
              Json.str(input, "instrumentId")));
    return new Result(outcome, List.copyOf(reasons), List.copyOf(paths));
  }

  private static void walk(
      String id,
      Map<String, Object> nodes,
      Map<String, Object> rules,
      List<String> path,
      List<Map<String, Object>> reasons,
      List<Map<String, Object>> paths) {
    var next = new ArrayList<>(path);
    next.add(id);
    if (path.contains(id)) {
      quality("RELATIONSHIP_CYCLE", "Cycle in underlying relationships", id, next, reasons, paths);
      return;
    }
    if (path.size() > 5) {
      quality("MAX_DEPTH", "Underlying traversal exceeds five edges", id, next, reasons, paths);
      return;
    }
    if (!nodes.containsKey(id)) {
      quality("UNKNOWN_INSTRUMENT", "Missing instrument metadata", id, next, reasons, paths);
      return;
    }
    var node = (Map<String, Object>) nodes.get(id);
    paths.add(
        Map.of(
            "instrumentId", id, "issuer", Objects.toString(node.get("issuer"), ""), "path", next));
    if (((List<?>) rules.getOrDefault("restrictedInstruments", List.of())).contains(id))
      reasons.add(
          Map.of(
              "code",
              "RESTRICTED_INSTRUMENT",
              "severity",
              "BLOCK",
              "message",
              id + " is restricted through " + String.join(" → ", next),
              "instrumentId",
              id,
              "path",
              next));
    String issuer = Json.str(node, "issuer");
    if (issuer.isBlank())
      quality("MISSING_ISSUER", "Instrument issuer is missing", id, next, reasons, paths);
    else if (((List<?>) rules.getOrDefault("restrictedIssuers", List.of())).contains(issuer))
      reasons.add(
          Map.of(
              "code",
              "RESTRICTED_ISSUER",
              "severity",
              "BLOCK",
              "message",
              "Issuer " + issuer + " is restricted",
              "instrumentId",
              id,
              "path",
              next));
    for (Object child : (List<?>) node.getOrDefault("underlyings", List.of()))
      walk(child.toString(), nodes, rules, next, reasons, paths);
  }

  private static void quality(
      String code,
      String message,
      String id,
      List<String> path,
      List<Map<String, Object>> reasons,
      List<Map<String, Object>> paths) {
    reasons.add(
        Map.of(
            "code",
            code,
            "severity",
            "REVIEW",
            "message",
            message,
            "instrumentId",
            id,
            "path",
            path));
    paths.add(Map.of("instrumentId", id, "path", path, "error", code));
  }
}
