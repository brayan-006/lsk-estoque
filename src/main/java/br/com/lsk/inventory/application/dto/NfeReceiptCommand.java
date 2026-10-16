package br.com.lsk.inventory.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record NfeReceiptCommand(String accessKey, String number, List<ReceiptItem> items) {

    public record ReceiptItem(String sku, String batchCode, BigDecimal quantity, BigDecimal unitCost,
                              LocalDate manufacturingDate, LocalDate expiryDate) {
    }
}
