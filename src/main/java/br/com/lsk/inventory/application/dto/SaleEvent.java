package br.com.lsk.inventory.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Sale confirmed event. The eventId is the idempotency key: the same eventId never deducts stock twice. */
public record SaleEvent(UUID eventId, String order, OffsetDateTime occurredAt, List<SaleItem> items) {

    public SaleEvent {
        Objects.requireNonNull(eventId, "eventId is required");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A sale event needs at least one item");
        }
        items = List.copyOf(items);
    }

    public record SaleItem(String sku, BigDecimal quantity, BigDecimal unitPrice) {

        public SaleItem {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku is required");
            }
            if (quantity == null || quantity.signum() <= 0) {
                throw new IllegalArgumentException("quantity must be greater than zero (sku " + sku + ")");
            }
            if (unitPrice != null && unitPrice.signum() < 0) {
                throw new IllegalArgumentException("unitPrice cannot be negative (sku " + sku + ")");
            }
        }
    }
}
