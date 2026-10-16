package br.com.lsk.inventory.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** ERP-neutral stock document. */
public record StockDocument(String reference, LocalDate data, String comment, List<Row> rows) {

    public record Row(String itemCode, BigDecimal quantity, BigDecimal unitCost, List<Batch> batches) {
    }

    public record Batch(String number, BigDecimal quantity, LocalDate manufacturing, LocalDate expiry) {
    }
}
