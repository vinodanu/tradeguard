package com.tradeguard.investigation;

import com.tradeguard.*;
import com.tradeguard.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cases")
@Transactional
public class CaseApi {
  private final SqlStore store;

  public CaseApi(SqlStore s) {
    store = s;
  }

  @GetMapping
  public Map<String, Object> list(
      @AuthenticationPrincipal Jwt j,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Problem.require(page >= 0 && size >= 1 && size <= 100, 400, "Invalid pagination");
    var all =
        store.list(CaseRow.class).stream()
            .filter(d -> Access.sees(j, d))
            .filter(d -> status.isBlank() || status.equals(d.get("status")))
            .sorted(Comparator.comparing(d -> Json.str(d, "createdAt"), Comparator.reverseOrder()))
            .toList();
    return Map.of(
        "items", all.stream().skip((long) page * size).limit(size).toList(), "total", all.size());
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    var d = store.view(store.find(CaseRow.class, id));
    Access.portfolio(j, Json.str(d, "portfolioId"));
    var result = Json.copy(d);
    result.put(
        "notes",
        store.list(NoteRow.class).stream().filter(n -> id.equals(n.get("caseId"))).toList());
    result.put(
        "actions",
        store.list(ActionRow.class).stream().filter(n -> id.equals(n.get("caseId"))).toList());
    return result;
  }

  @PostMapping("/{id}/assign")
  public Map<String, Object> assign(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @RequestBody Map<String, Object> b) {
    Access.roles(j, "REVIEWER", "ADMIN");
    String scope = "assign:" + j.getSubject() + ":" + id;
    var old = store.previous(scope, key, b);
    if (old != null) return old;
    var row = store.lock(CaseRow.class, id);
    var d = store.view(row);
    Access.portfolio(j, Json.str(d, "portfolioId"));
    Access.expected(b, row.version);
    Problem.require(!"RESOLVED".equals(d.get("status")), 409, "Case is resolved");
    String assignee = b.containsKey("assignee") ? Json.str(b, "assignee") : j.getSubject();
    Problem.require(Set.of("reviewer", "admin").contains(assignee), 400, "Unknown reviewer");
    Problem.require(
        "ADMIN".equals(Access.role(j)) || assignee.equals(j.getSubject()),
        403,
        "Only admins can assign another reviewer");
    Problem.require(
        Json.str(d, "assignedTo").isBlank()
            || Json.str(d, "assignedTo").equals(j.getSubject())
            || "ADMIN".equals(Access.role(j)),
        409,
        "Case already assigned");
    d.put("assignedTo", assignee);
    d.put("status", "IN_PROGRESS");
    store.update(row, d);
    action(row, "ASSIGNED", j.getSubject(), assignee);
    changed(row);
    var result = store.view(row);
    store.remember(scope, key, b, result);
    return result;
  }

  @PostMapping("/{id}/notes")
  public Map<String, Object> note(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @RequestBody Map<String, Object> b) {
    Access.roles(j, "REVIEWER", "ADMIN");
    Access.note(b);
    String scope = "note:" + j.getSubject() + ":" + id;
    var old = store.previous(scope, key, b);
    if (old != null) return old;
    var row = store.lock(CaseRow.class, id);
    var d = store.view(row);
    Access.portfolio(j, Json.str(d, "portfolioId"));
    Access.expected(b, row.version);
    Problem.require(!"RESOLVED".equals(d.get("status")), 409, "Case is resolved");
    String noteId = UUID.randomUUID().toString();
    var result =
        store.view(
            store.insert(
                NoteRow.class,
                noteId,
                Map.of(
                    "caseId",
                    id,
                    "actor",
                    j.getSubject(),
                    "note",
                    Json.str(b, "note"),
                    "at",
                    Instant.now().toString())));
    d.put("lastNoteAt", Instant.now().toString());
    store.update(row, d);
    changed(row);
    store.remember(scope, key, b, result);
    return result;
  }

  @PostMapping("/{id}/resolve")
  public Map<String, Object> resolve(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key,
      @RequestBody Map<String, Object> b) {
    Access.roles(j, "REVIEWER", "ADMIN");
    Access.note(b);
    String scope = "resolve:" + j.getSubject() + ":" + id;
    var old = store.previous(scope, key, b);
    if (old != null) return old;
    var row = store.lock(CaseRow.class, id);
    var d = store.view(row);
    Access.portfolio(j, Json.str(d, "portfolioId"));
    Access.expected(b, row.version);
    Problem.require(!"RESOLVED".equals(d.get("status")), 409, "Case is already resolved");
    Problem.require(
        !"PRE_TRADE".equals(d.get("workflow")) || !j.getSubject().equals(d.get("traderId")),
        403,
        "Cannot review your own order");
    Problem.require(
        Json.str(d, "assignedTo").equals(j.getSubject()) || "ADMIN".equals(Access.role(j)),
        409,
        "Assign the case to yourself before resolving");
    String disposition = Json.str(b, "disposition");
    Set<String> allowed =
        "PRE_TRADE".equals(d.get("workflow"))
            ? Set.of("APPROVED", "REJECTED")
            : Set.of("CONFIRMED", "FALSE_POSITIVE", "DATA_ISSUE");
    Problem.require(allowed.contains(disposition), 400, "Invalid disposition");
    d.put("status", "RESOLVED");
    d.put("disposition", disposition);
    d.put("resolvedBy", j.getSubject());
    d.put("resolvedAt", Instant.now().toString());
    d.put("resolutionNote", Json.str(b, "note"));
    store.update(row, d);
    action(row, "RESOLVED", j.getSubject(), Json.str(b, "note"));
    store.emit(
        Event.create(
            "CaseResolved", row.id, row.version, Json.str(d, "orderId"), null, store.view(row)));
    var result = store.view(row);
    store.remember(scope, key, b, result);
    return result;
  }

  @KafkaListener(topics = "tradeguard.compliance.v1")
  public void events(String raw) {
    Event e = Json.read(raw, Event.class);
    e.validate();
    if (store.seen(e.eventId())) return;
    var p = e.payload();
    boolean pre = e.eventType().equals("PreTradeEvaluated") && p.get("outcome").equals("REVIEW");
    boolean post = e.eventType().equals("PostTradeEvaluated") && !p.get("outcome").equals("ALLOW");
    if (pre || post) {
      String id = Json.id("case:" + p.get("evaluationId"));
      if (store.optional(CaseRow.class, id) == null) {
        var d = Json.copy(p);
        d.put("status", "OPEN");
        d.put("assignedTo", "");
        d.put("severity", p.get("outcome").equals("BLOCK") ? "HIGH" : "MEDIUM");
        d.put("createdAt", Instant.now().toString());
        var row = store.insert(CaseRow.class, id, d);
        store.emit(
            Event.create("CaseOpened", id, 0, e.correlationId(), e.eventId(), store.view(row)));
      }
    }
    store.inbox(e.eventId());
  }

  private void action(CaseRow row, String name, String actor, String note) {
    store.insert(
        ActionRow.class,
        UUID.randomUUID().toString(),
        Map.of(
            "caseId",
            row.id,
            "action",
            name,
            "actor",
            actor,
            "note",
            note,
            "at",
            Instant.now().toString()));
  }

  private void changed(CaseRow row) {
    store.emit(
        Event.create(
            "CaseChanged",
            row.id,
            row.version,
            Json.str(Json.map(row.data), "orderId"),
            null,
            store.view(row)));
  }
}
