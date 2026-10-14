package com.tradeguard.trade;

import com.tradeguard.*;
import com.tradeguard.sql.*;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Seed implements ApplicationRunner {
  private final SqlStore store;

  public Seed(SqlStore s) {
    store = s;
  }

  @Transactional
  public void run(ApplicationArguments a) {
    String pw = System.getenv("DEMO_PASSWORD");
    Problem.require(
        pw != null && pw.length() >= 12, 500, "Set DEMO_PASSWORD to at least 12 characters");
    for (String p : List.of("PORTFOLIO_A", "PORTFOLIO_B"))
      if (store.optional(PortfolioRow.class, p) == null)
        store.insert(
            PortfolioRow.class,
            p,
            Map.of(
                "name",
                p.equals("PORTFOLIO_A") ? "Growth Fund" : "Income Fund",
                "currency",
                "USD"));
    for (var entry :
        Map.of(
                "trader",
                "TRADER",
                "trader2",
                "TRADER",
                "reviewer",
                "REVIEWER",
                "admin",
                "ADMIN",
                "viewer",
                "VIEWER")
            .entrySet()) {
      String id = Json.id("user:" + entry.getKey());
      if (store.optional(UserRow.class, id) == null) {
        List<String> portfolios =
            entry.getKey().equals("trader")
                ? List.of("PORTFOLIO_A")
                : entry.getKey().equals("trader2")
                    ? List.of("PORTFOLIO_B")
                    : List.of("PORTFOLIO_A", "PORTFOLIO_B");
        store.insert(
            UserRow.class,
            id,
            Map.of(
                "username",
                entry.getKey(),
                "role",
                entry.getValue(),
                "passwordHash",
                new BCryptPasswordEncoder().encode(pw),
                "portfolios",
                portfolios));
        for (String p : portfolios)
          store.insert(
              MemberRow.class, Json.id(id + ":" + p), Map.of("userId", id, "portfolioId", p));
      }
    }
  }
}
