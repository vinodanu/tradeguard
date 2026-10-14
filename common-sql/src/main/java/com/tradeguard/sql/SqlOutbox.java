package com.tradeguard.sql;

import jakarta.persistence.*;

@Entity
@Table(name = "outbox")
public class SqlOutbox extends SqlRow {
  public SqlOutbox() {}
}
