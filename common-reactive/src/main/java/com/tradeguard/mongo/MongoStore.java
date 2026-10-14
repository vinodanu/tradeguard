package com.tradeguard.mongo;

import com.tradeguard.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.*;

@Component
public class MongoStore {
  public final ReactiveMongoTemplate db;
  public final TransactionalOperator tx;
  private final KafkaTemplate<String, String> kafka;
  private final AtomicBoolean publishing = new AtomicBoolean();

  public MongoStore(
      ReactiveMongoTemplate db,
      org.springframework.data.mongodb.ReactiveMongoDatabaseFactory f,
      KafkaTemplate<String, String> kafka) {
    this.db = db;
    this.tx =
        TransactionalOperator.create(
            new org.springframework.data.mongodb.ReactiveMongoTransactionManager(f));
    this.kafka = kafka;
  }

  public Mono<Map<String, Object>> get(String collection, String id) {
    return db.findById(id, Document.class, collection)
        .map(MongoStore::map)
        .switchIfEmpty(Mono.error(new Problem(404, "Record not found")));
  }

  public Flux<Map<String, Object>> list(String collection) {
    return db.findAll(Document.class, collection).map(MongoStore::map);
  }

  public Mono<Map<String, Object>> insert(String collection, String id, Map<String, Object> data) {
    var d = Document.parse(Json.write(data));
    d.put("_id", id);
    return db.insert(d, collection).map(MongoStore::map);
  }

  public Mono<Map<String, Object>> save(String collection, String id, Map<String, Object> data) {
    var d = Document.parse(Json.write(data));
    d.put("_id", id);
    return db.save(d, collection).map(MongoStore::map);
  }

  public Mono<Boolean> seen(String id) {
    return db.exists(Query.query(Criteria.where("_id").is(id)), "processed_events");
  }

  public Mono<Void> inbox(String id) {
    return insert("processed_events", id, Map.of("processedAt", java.time.Instant.now().toString()))
        .then();
  }

  public Mono<Void> emit(Event e) {
    return insert("outbox", e.eventId(), Map.of("event", Json.write(e), "published", false)).then();
  }

  public static Map<String, Object> map(Document d) {
    var m = Json.map(d.toJson());
    m.put("id", m.remove("_id"));
    return m;
  }

  @Scheduled(fixedDelay = 500)
  public void publish() {
    if (!publishing.compareAndSet(false, true)) return;
    db.find(Query.query(Criteria.where("published").is(false)).limit(50), Document.class, "outbox")
        .concatMap(
            d -> {
              Event e = Json.read(d.getString("event"), Event.class);
              return Mono.fromFuture(kafka.send(e.topic(), e.aggregateId(), Json.write(e)))
                  .then(
                      db.updateFirst(
                          Query.query(Criteria.where("_id").is(d.get("_id"))),
                          new Update().set("published", true),
                          "outbox"));
            })
        .doFinally(s -> publishing.set(false))
        .subscribe(
            v -> {},
            e ->
                org.slf4j.LoggerFactory.getLogger(MongoStore.class)
                    .warn("Outbox publication postponed: {}", e.getClass().getSimpleName()));
  }
}
