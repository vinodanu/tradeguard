package com.tradeguard.trade;

import com.tradeguard.sql.SqlRow;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserRow extends SqlRow {
  public UserRow() {}
}
