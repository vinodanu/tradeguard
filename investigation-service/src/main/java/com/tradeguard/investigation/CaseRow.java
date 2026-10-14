package com.tradeguard.investigation;

import com.tradeguard.sql.SqlRow;
import jakarta.persistence.*;

@Entity
@Table(name = "cases")
public class CaseRow extends SqlRow {
  public CaseRow() {}
}
