package com.tradeguard.trade;

import com.tradeguard.sql.SqlRow;
import jakarta.persistence.*;

@Entity
@Table(name = "portfolio_members")
public class MemberRow extends SqlRow {
  public MemberRow() {}
}
