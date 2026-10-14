package br.com.lsk.estoque.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Sale confirmed event. The eventId is the idempotency key: the same eventId never deducts stock twice. */
public record VendaEvent(UUID eventId, String pedido, OffsetDateTime ocorridoEm, List<ItemVenda> itens) {

    public VendaEvent {
        Objects.requireNonNull(eventId, "eventId is required");
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("A sale event needs at least one item");
        }
        itens = List.copyOf(itens);
    }

    public record ItemVenda(String sku, BigDecimal quantidade, BigDecimal precoUnitario) {

        public ItemVenda {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku is required");
            }
            if (quantidade == null || quantidade.signum() <= 0) {
                throw new IllegalArgumentException("quantity must be greater than zero (sku " + sku + ")");
            }
            if (precoUnitario != null && precoUnitario.signum() < 0) {
                throw new IllegalArgumentException("precoUnitario cannot be negative (sku " + sku + ")");
            }
        }
    }
}
