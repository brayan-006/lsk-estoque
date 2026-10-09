package br.com.lsk.estoque.application.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AlertaEstoqueEvent(Long alertaId, String sku, BigDecimal saldo, BigDecimal estoqueMinimo,
                                 String mensagem, OffsetDateTime geradoEm) {
}
