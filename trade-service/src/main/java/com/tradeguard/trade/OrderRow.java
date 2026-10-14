package com.tradeguard.trade;

import com.tradeguard.sql.SqlRow;
import jakarta.persistence.*;

@Entity
@Table(name = "orders")
public class OrderRow extends SqlRow {
  public OrderRow() {}
}
