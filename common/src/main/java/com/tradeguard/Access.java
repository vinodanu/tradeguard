package com.tradeguard;

import java.util.*;
import org.springframework.security.oauth2.jwt.Jwt;

public final class Access {
  public static String role(Jwt jwt) {
    return jwt.getClaimAsString("role");
  }

  public static void roles(Jwt jwt, String... roles) {
    Problem.require(
        jwt != null && Arrays.asList(roles).contains(role(jwt)),
        403,
        "This role cannot perform this action");
  }

  public static void portfolio(Jwt jwt, String id) {
    List<String> ids = jwt.getClaimAsStringList("portfolios");
    Problem.require(ids != null && ids.contains(id), 403, "Portfolio access denied");
  }

  public static boolean sees(Jwt jwt, Map<String, Object> row) {
    List<String> ids = jwt.getClaimAsStringList("portfolios");
    return ids != null && ids.contains(Json.str(row, "portfolioId"));
  }

  public static void expected(Map<String, Object> body, long version) {
    Problem.require(
        body.get("expectedVersion") instanceof Number, 400, "expectedVersion is required");
    Problem.require(
        Json.number(body, "expectedVersion") == version, 409, "Record changed; refresh and retry");
  }

  public static void note(Map<String, Object> body) {
    Problem.require(
        Json.str(body, "note").trim().length() >= 5 && Json.str(body, "note").length() <= 2000,
        400,
        "A note of 5–2000 characters is required");
  }
}
