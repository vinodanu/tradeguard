package com.tradeguard.investigation;

import com.tradeguard.sql.SqlRow;
import jakarta.persistence.*;

@Entity
@Table(name = "case_notes")
public class NoteRow extends SqlRow {
  public NoteRow() {}
}
