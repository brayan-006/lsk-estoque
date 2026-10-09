package br.com.lsk.estoque.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de venda confirmada (payload JSON da fila lsk.estoque.vendas.q).
 * O eventId é a chave de idempotência: o mesmo eventId nunca baixa estoque duas vezes.
 */
public record VendaEvent(UUID eventId, String pedido, OffsetDateTime ocorridoEm, List<ItemVenda> itens) {

    public VendaEvent {
        Objects.requireNonNull(eventId, "eventId é obrigatório");
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("O evento de venda precisa ter ao menos um item");
        }
        itens = List.copyOf(itens);
    }

    public record ItemVenda(String sku, BigDecimal quantidade, BigDecimal precoUnitario) {

        public ItemVenda {
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku é obrigatório");
            }
            if (quantidade == null || quantidade.signum() <= 0) {
                throw new IllegalArgumentException("quantidade deve ser maior que zero (sku " + sku + ")");
            }
            if (precoUnitario != null && precoUnitario.signum() < 0) {
                throw new IllegalArgumentException("precoUnitario não pode ser negativo (sku " + sku + ")");
            }
        }
    }
}
