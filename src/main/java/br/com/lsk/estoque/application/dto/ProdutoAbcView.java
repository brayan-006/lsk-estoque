package br.com.lsk.estoque.application.dto;

import br.com.lsk.estoque.domain.model.ClasseAbc;

import java.math.BigDecimal;

public record ProdutoAbcView(String sku, String descricao, ClasseAbc classeAbc, BigDecimal precoVenda,
                             BigDecimal saldoDisponivel) {
}
