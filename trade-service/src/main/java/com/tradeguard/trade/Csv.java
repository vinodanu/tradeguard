package com.tradeguard.trade;

import com.tradeguard.*;
import java.time.*;
import java.util.*;

public final class Csv {
  public static final String HEADER =
      "source,externalTradeId,portfolioId,instrumentId,side,quantity,price,executedAt";

  public static List<Map<String, Object>> validate(String csv) {
    String[] lines = csv.strip().split("\\R");
    Problem.require(lines.length >= 2 && lines.length <= 101, 400, "CSV requires 1–100 data rows");
    Problem.require(lines[0].equals(HEADER), 400, "CSV header must be " + HEADER);
    var result = new ArrayList<Map<String, Object>>();
    var errors = new ArrayList<String>();
    Set<String> keys = new HashSet<>();
    for (int i = 1; i < lines.length; i++) {
      try {
        String[] c = lines[i].split(",", -1);
        Problem.require(c.length == 8, 400, "Expected 8 unquoted fields");
        Problem.require(
            c[0].matches("[A-Za-z0-9_-]{1,64}") && c[1].matches("[A-Za-z0-9_-]{1,64}"),
            400,
            "Invalid source or externalTradeId");
        var d =
            TradeApi.validated(
                Map.of(
                    "portfolioId",
                    c[2],
                    "instrumentId",
                    c[3],
                    "side",
                    c[4],
                    "quantity",
                    c[5],
                    "price",
                    c[6]));
        Instant time = Instant.parse(c[7]);
        Problem.require(!time.isAfter(Instant.now()), 400, "Execution cannot be in the future");
        Problem.require(keys.add(c[0] + ":" + c[1]), 400, "Duplicate external trade ID in file");
        d.put("source", c[0]);
        d.put("externalTradeId", c[1]);
        d.put("executedAt", time.toString());
        result.add(d);
      } catch (Exception e) {
        errors.add("Row " + (i + 1) + ": " + e.getMessage());
      }
    }
    Problem.require(errors.isEmpty(), 400, String.join("; ", errors));
    return result;
  }
}
