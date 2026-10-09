package br.com.lsk.estoque.application.event;

import br.com.lsk.estoque.domain.model.TipoMovimentacao;

/** Publicado dentro da transação; consumido APÓS o commit para notificar o ERP. */
public record MovimentacaoRegistradaEvent(String referencia, TipoMovimentacao tipo) {
}
