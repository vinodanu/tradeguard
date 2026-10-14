package com.tradeguard.sql;

import com.tradeguard.*;
import jakarta.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SqlStore {
  @PersistenceContext EntityManager em;
  private final KafkaTemplate<String, String> kafka;

  public SqlStore(KafkaTemplate<String, String> kafka) {
    this.kafka = kafka;
  }

  public <T extends SqlRow> T find(Class<T> type, String id) {
    T r = em.find(type, id);
    if (r == null) throw new Problem(404, "Record not found");
    return r;
  }

  public <T extends SqlRow> T lock(Class<T> type, String id) {
    T r = em.find(type, id, LockModeType.PESSIMISTIC_WRITE);
    if (r == null) throw new Problem(404, "Record not found");
    return r;
  }

  public <T extends SqlRow> T optional(Class<T> type, String id) {
    return em.find(type, id);
  }

  public <T extends SqlRow> T insert(Class<T> type, String id, Map<String, Object> data) {
    try {
      T r = type.getDeclaredConstructor().newInstance();
      r.id = id;
      r.data = Json.write(data);
      em.persist(r);
      return r;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  public Map<String, Object> view(SqlRow r) {
    var m = Json.copy(Json.map(r.data));
    m.put("id", r.id);
    m.put("version", r.version == null ? 0 : r.version);
    return m;
  }

  public void update(SqlRow r, Map<String, Object> data) {
    r.data = Json.write(data);
    em.flush();
  }

  public <T extends SqlRow> long count(Class<T> type) {
    return em.createQuery("select count(r) from " + type.getSimpleName() + " r", Long.class)
        .getSingleResult();
  }

  public <T extends SqlRow> List<Map<String, Object>> list(Class<T> type) {
    return em
        .createQuery("select r from " + type.getSimpleName() + " r", type)
        .getResultList()
        .stream()
        .map(this::view)
        .toList();
  }

  public boolean seen(String id) {
    return optional(SqlInbox.class, id) != null;
  }

  public void inbox(String id) {
    insert(SqlInbox.class, id, Map.of("processedAt", java.time.Instant.now().toString()));
  }

  public void emit(Event event) {
    insert(
        SqlOutbox.class, event.eventId(), Map.of("event", Json.write(event), "published", false));
  }

  public Map<String, Object> previous(String scope, String key, Object request) {
    Problem.require(
        key != null && key.length() >= 8 && key.length() <= 128,
        400,
        "Idempotency-Key must contain 8–128 characters");
    var row = optional(Idempotency.class, Json.id(scope + ":" + key));
    if (row == null) return null;
    var data = Json.map(row.data);
    Problem.require(
        Json.str(data, "hash").equals(Json.hash(request)),
        409,
        "Idempotency key was used for different input");
    return Json.map(Json.str(data, "response"));
  }

  public void remember(String scope, String key, Object request, Object response) {
    insert(
        Idempotency.class,
        Json.id(scope + ":" + key),
        Map.of("hash", Json.hash(request), "response", Json.write(response)));
  }

  @Scheduled(fixedDelay = 500)
  @Transactional
  public void publish() {
    var rows =
        em.createQuery(
                "select r from SqlOutbox r where r.data like :pending order by r.id",
                SqlOutbox.class)
            .setParameter("pending", "%\"published\":false%")
            .setMaxResults(50)
            .getResultList();
    for (var row : rows) {
      var d = Json.map(row.data);
      var e = Json.read(Json.str(d, "event"), Event.class);
      try {
        kafka.send(e.topic(), e.aggregateId(), Json.write(e)).get(5, TimeUnit.SECONDS);
        d.put("published", true);
        row.data = Json.write(d);
      } catch (Exception ex) {
        org.slf4j.LoggerFactory.getLogger(SqlStore.class)
            .warn("Outbox publication postponed event={}", e.eventId());
        break;
      }
    }
  }
}
