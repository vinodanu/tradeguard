package com.tradeguard.investigation;

import com.tradeguard.sql.SqlRow;
import jakarta.persistence.*;

@Entity
@Table(name = "case_actions")
public class ActionRow extends SqlRow {
  public ActionRow() {}
}
