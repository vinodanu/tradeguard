package com.tradeguard.trade;

import com.tradeguard.sql.SqlRow;
import jakarta.persistence.*;

@Entity
@Table(name = "trade_audit")
public class AuditRow extends SqlRow {
  public AuditRow() {}
}
