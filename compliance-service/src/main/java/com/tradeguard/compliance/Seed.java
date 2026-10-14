package com.tradeguard.compliance;

import com.tradeguard.*;
import com.tradeguard.mongo.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;
import reactor.core.publisher.*;

@Component
public class Seed implements ApplicationRunner {
  private final MongoStore store;

  public Seed(MongoStore s) {
    store = s;
  }

  public void run(ApplicationArguments a) {
    Flux.fromIterable(
            List.of(
                "instrument_datasets",
                "rulesets",
                "active_configuration",
                "configurations",
                "evaluations",
                "processed_events",
                "outbox",
                "idempotency"))
        .concatMap(
            c ->
                store
                    .db
                    .collectionExists(c)
                    .flatMap(exists -> exists ? Mono.empty() : store.db.createCollection(c).then()))
        .then()
        .block(Duration.ofSeconds(30));
    store
        .get("active_configuration", "active")
        .onErrorResume(
            Problem.class,
            e -> {
              if (e.status != 404) return Mono.error(e);
              var instruments = new LinkedHashMap<String, Object>();
              instruments.put(
                  "ACME",
                  Map.of(
                      "name", "Acme Industries", "issuer", "ACME_CORP", "underlyings", List.of()));
              instruments.put(
                  "NOVA",
                  Map.of("name", "Nova Research", "issuer", "NOVA_CORP", "underlyings", List.of()));
              instruments.put(
                  "BANNED",
                  Map.of(
                      "name",
                      "Restricted Equity",
                      "issuer",
                      "BANNED_CORP",
                      "underlyings",
                      List.of()));
              instruments.put(
                  "FUND_X",
                  Map.of(
                      "name",
                      "Synthetic Growth Basket",
                      "issuer",
                      "FUND_MANAGER",
                      "underlyings",
                      List.of("ACME", "BANNED")));
              instruments.put(
                  "CYCLE_A",
                  Map.of(
                      "name",
                      "Cycle Fixture A",
                      "issuer",
                      "FIXTURE",
                      "underlyings",
                      List.of("CYCLE_B")));
              instruments.put(
                  "CYCLE_B",
                  Map.of(
                      "name",
                      "Cycle Fixture B",
                      "issuer",
                      "FIXTURE",
                      "underlyings",
                      List.of("CYCLE_A")));
              instruments.put(
                  "MISSING_REF",
                  Map.of(
                      "name",
                      "Missing Reference Basket",
                      "issuer",
                      "FIXTURE",
                      "underlyings",
                      List.of("DOES_NOT_EXIST")));
              var rule =
                  Map.<String, Object>of(
                      "name",
                      "Initial restrictions",
                      "restrictedInstruments",
                      List.of("BANNED"),
                      "restrictedIssuers",
                      List.of("BANNED_CORP"),
                      "maxNotional",
                      "100000",
                      "published",
                      true,
                      "createdAt",
                      "2000-01-01T00:00:00Z");
              var config =
                  Map.<String, Object>of(
                      "rulesetId",
                      "rules-v1",
                      "datasetId",
                      "instruments-v1",
                      "version",
                      1,
                      "effectiveAt",
                      "2000-01-01T00:00:00Z");
              return store.tx.transactional(
                  store
                      .insert(
                          "instrument_datasets",
                          "instruments-v1",
                          Map.of("version", 1, "instruments", instruments))
                      .then(store.insert("rulesets", "rules-v1", rule))
                      .then(store.insert("configurations", "config-1", config))
                      .then(store.insert("active_configuration", "active", config)));
            })
        .block(Duration.ofSeconds(30));
    store
        .db
        .indexOps("configurations")
        .ensureIndex(new Index().on("effectiveAt", Sort.Direction.ASC))
        .block();
    store
        .db
        .indexOps("evaluations")
        .ensureIndex(new Index().on("portfolioId", Sort.Direction.ASC))
        .block();
  }
}
