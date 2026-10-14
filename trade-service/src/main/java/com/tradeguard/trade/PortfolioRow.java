package com.tradeguard.trade;

import com.tradeguard.sql.SqlRow;
import jakarta.persistence.*;

@Entity
@Table(name = "portfolios")
public class PortfolioRow extends SqlRow {
  public PortfolioRow() {}
}
