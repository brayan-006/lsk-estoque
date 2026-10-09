package br.com.lsk.estoque.application.dto;

import br.com.lsk.estoque.domain.model.CriterioBaixa;

import java.math.BigDecimal;

/** Linha devolvida por pr_dar_baixa_fefo. */
public record LoteBaixado(Long movimentacaoId, Long loteId, String codigoLote, BigDecimal quantidade,
                          BigDecimal custoUnitario, CriterioBaixa criterio) {
}
