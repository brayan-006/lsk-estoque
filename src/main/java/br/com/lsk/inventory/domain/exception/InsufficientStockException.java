package br.com.lsk.inventory.domain.exception;

import java.math.BigDecimal;

public class InsufficientStockException extends BusinessRuleException {
    public InsufficientStockException(String sku, BigDecimal requested) {
        super("Insufficient stock for SKU " + sku + " (requested: " + requested.stripTrailingZeros().toPlainString() + ")");
    }
}
