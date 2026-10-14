package com.tradeguard.trade;

import static org.junit.jupiter.api.Assertions.*;

import com.tradeguard.*;
import java.util.*;
import org.junit.jupiter.api.*;

class CsvTest {
  String row = "demo,one,PORTFOLIO_A,ACME,BUY,10,100,2025-01-01T12:00:00Z";

  @Test
  void parsesNewlineSeparatedRows() {
    assertEquals(1, Csv.validate(Csv.HEADER + "\n" + row).size());
  }

  @Test
  void parsesWindowsLineEndings() {
    assertEquals(1, Csv.validate(Csv.HEADER + "\r\n" + row).size());
  }

  @Test
  void validatesWholeFile() {
    var e = assertThrows(Problem.class, () -> Csv.validate(Csv.HEADER + "\n" + row + "\ninvalid"));
    assertTrue(e.getMessage().contains("Row 3"));
  }

  @Test
  void duplicateWithinFileRejected() {
    assertThrows(Problem.class, () -> Csv.validate(Csv.HEADER + "\n" + row + "\n" + row));
  }

  @Test
  void moreThan100RowsRejected() {
    assertThrows(Problem.class, () -> Csv.validate(Csv.HEADER + "\n" + (row + "\n").repeat(101)));
  }

  @Test
  void futureExecutionRejected() {
    assertThrows(
        Problem.class,
        () ->
            Csv.validate(
                Csv.HEADER + "\n" + row.replace("2025-01-01T12:00:00Z", "2999-01-01T00:00:00Z")));
  }

  @Test
  void wrongHeaderRejected() {
    assertThrows(Problem.class, () -> Csv.validate("wrong\n" + row));
  }

  @Test
  void decimalNumbersMustBeStrings() {
    assertThrows(
        Problem.class,
        () ->
            TradeApi.validated(
                Map.of(
                    "portfolioId",
                    "A",
                    "instrumentId",
                    "ACME",
                    "side",
                    "BUY",
                    "quantity",
                    10,
                    "price",
                    "100")));
  }

  @Test
  void negativeQuantityRejected() {
    assertThrows(
        Problem.class,
        () ->
            TradeApi.validated(
                Map.of(
                    "portfolioId",
                    "A",
                    "instrumentId",
                    "ACME",
                    "side",
                    "BUY",
                    "quantity",
                    "-1",
                    "price",
                    "100")));
  }

  @Test
  void excessiveScaleRejected() {
    assertThrows(
        Problem.class,
        () ->
            TradeApi.validated(
                Map.of(
                    "portfolioId",
                    "A",
                    "instrumentId",
                    "ACME",
                    "side",
                    "BUY",
                    "quantity",
                    "0.0000001",
                    "price",
                    "100")));
  }
}
