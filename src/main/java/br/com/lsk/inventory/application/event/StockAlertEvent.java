package br.com.lsk.inventory.application.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StockAlertEvent(Long alertId, String sku, BigDecimal balance, BigDecimal minimumStock,
                                 String message, OffsetDateTime generatedEm) {
}
