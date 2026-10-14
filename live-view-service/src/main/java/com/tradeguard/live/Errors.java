package com.tradeguard.live;

import com.tradeguard.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(Problem.class)
  ResponseEntity<ProblemDetail> problem(Problem e) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.status), e.getMessage());
    p.setTitle(HttpStatus.valueOf(e.status).getReasonPhrase());
    return ResponseEntity.status(e.status).body(p);
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.web.bind.support.WebExchangeBindException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Invalid request: " + e.getMessage()));
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return ResponseEntity.status(409)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Concurrent request conflict; retry with the same idempotency key"));
  }
}
