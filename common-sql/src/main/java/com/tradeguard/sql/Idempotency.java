package com.tradeguard.sql;

import jakarta.persistence.*;

@Entity
@Table(name = "idempotency")
public class Idempotency extends SqlRow {
  public Idempotency() {}
}
