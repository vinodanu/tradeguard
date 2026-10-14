package com.tradeguard;

import java.time.Instant;
import java.util.*;

public record Event(
    String eventId,
    String eventType,
    int schemaVersion,
    String aggregateId,
    long aggregateVersion,
    String occurredAt,
    String correlationId,
    String causationId,
    Map<String, Object> payload) {
  public static Event create(
      String type,
      String aggregate,
      long version,
      String correlation,
      String cause,
      Map<String, Object> payload) {
    return new Event(
        UUID.randomUUID().toString(),
        type,
        1,
        aggregate,
        version,
        Instant.now().toString(),
        correlation,
        cause,
        payload);
  }

  public String topic() {
    return switch (eventType) {
      case "OrderSubmitted", "TradeExecuted", "OrderChanged" -> "tradeguard.trade.v1";
      case "PreTradeEvaluated", "PostTradeEvaluated", "RulesPublished" ->
          "tradeguard.compliance.v1";
      default -> "tradeguard.case.v1";
    };
  }

  public void validate() {
    if (schemaVersion != 1
        || eventId == null
        || aggregateId == null
        || aggregateId.isBlank()
        || payload == null
        || eventType == null
        || correlationId == null
        || aggregateVersion < 0) throw new IllegalArgumentException("Unsupported event envelope");
    UUID.fromString(eventId);
    Instant.parse(occurredAt);
    if (!Set.of(
            "OrderSubmitted",
            "TradeExecuted",
            "OrderChanged",
            "PreTradeEvaluated",
            "PostTradeEvaluated",
            "RulesPublished",
            "CaseOpened",
            "CaseChanged",
            "CaseResolved")
        .contains(eventType)) throw new IllegalArgumentException("Unsupported event type");
  }
}
