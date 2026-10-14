package br.com.lsk.estoque.application.event;

import br.com.lsk.estoque.domain.model.TipoMovimentacao;

/** Published inside the transaction and handled after the commit. */
public record MovimentacaoRegistradaEvent(String referencia, TipoMovimentacao tipo) {
}
