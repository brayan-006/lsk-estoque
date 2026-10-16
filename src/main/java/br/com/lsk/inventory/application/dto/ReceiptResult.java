package br.com.lsk.inventory.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReceiptResult(String reference, int itemsProcessed, List<BatchReceipt> batches) {

    public record BatchReceipt(String sku, String batchCode, BigDecimal quantity, BigDecimal skuBalance) {
    }
}
