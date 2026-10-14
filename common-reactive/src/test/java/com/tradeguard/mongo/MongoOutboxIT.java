package com.tradeguard.mongo;

import static org.junit.jupiter.api.Assertions.*;

import com.tradeguard.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.testcontainers.containers.MongoDBContainer;
import reactor.core.publisher.Mono;

class MongoOutboxIT {
  @Test
  void reactiveTransactionsRollbackAndCommitAtomically() throws Exception {
    try (var mongo = new MongoDBContainer("mongo:8.0.15")) {
      mongo.start();
      var factory =
          new SimpleReactiveMongoDatabaseFactory(
              new com.mongodb.ConnectionString(mongo.getReplicaSetUrl()));
      var db = new ReactiveMongoTemplate(factory);
      for (String name : List.of("processed_events", "outbox")) db.createCollection(name).block();
      var store = new MongoStore(db, factory, null);
      var event =
          Event.create("PreTradeEvaluated", "order", 0, "order", null, Map.of("outcome", "ALLOW"));
      assertThrows(
          IllegalStateException.class,
          () ->
              store
                  .tx
                  .transactional(
                      store
                          .inbox("input")
                          .then(store.emit(event))
                          .then(Mono.error(new IllegalStateException("crash"))))
                  .block());
      assertFalse(store.seen("input").block());
      assertEquals(0, store.list("outbox").count().block());
      store.tx.transactional(store.inbox("input").then(store.emit(event))).block();
      assertTrue(store.seen("input").block());
      assertEquals(1, store.list("outbox").count().block());
      factory.destroy();
    }
  }
}
