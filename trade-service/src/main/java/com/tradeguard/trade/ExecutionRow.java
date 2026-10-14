package com.tradeguard.trade;

import com.tradeguard.sql.SqlRow;
import jakarta.persistence.*;

@Entity
@Table(name = "executions")
public class ExecutionRow extends SqlRow {
  public ExecutionRow() {}
}
