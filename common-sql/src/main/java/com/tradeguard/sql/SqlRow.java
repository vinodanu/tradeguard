package com.tradeguard.sql;

import jakarta.persistence.*;

@MappedSuperclass
public abstract class SqlRow {
  @Id
  @Column(length = 64)
  public String id;

  @Lob
  @Column(columnDefinition = "LONGTEXT", nullable = false)
  public String data;

  @Version public Long version;
}
