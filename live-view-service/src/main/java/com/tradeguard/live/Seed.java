package com.tradeguard.live;

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
                "order_views",
                "case_views",
                "dashboard_counters",
                "feed_events",
                "processed_events",
                "outbox",
                "dead_letters",
                "projection_generation",
                "operations"))
        .concatMap(
            c ->
                store
                    .db
                    .collectionExists(c)
                    .flatMap(exists -> exists ? Mono.empty() : store.db.createCollection(c).then()))
        .then()
        .block(Duration.ofSeconds(30));
    store
        .get("projection_generation", "active")
        .onErrorResume(
            com.tradeguard.Problem.class,
            e ->
                e.status == 404
                    ? store.insert(
                        "projection_generation",
                        "active",
                        Map.of("orders", "order_views", "cases", "case_views"))
                    : Mono.error(e))
        .block();
    store
        .db
        .indexOps("feed_events")
        .ensureIndex(new Index().on("sequence", Sort.Direction.ASC).unique())
        .block();
  }
}
