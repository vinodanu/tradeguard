package com.tradeguard.sql;

import jakarta.persistence.*;

@Entity
@Table(name = "processed_events")
public class SqlInbox extends SqlRow {
  public SqlInbox() {}
}
