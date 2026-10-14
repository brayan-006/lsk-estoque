package br.com.lsk.estoque.application.dto;

import br.com.lsk.estoque.domain.model.ClasseAbc;
import br.com.lsk.estoque.domain.model.CriterioBaixa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EstoqueDetalhe(String sku, String descricao, String unidade, ClasseAbc classeAbc,
                             BigDecimal saldoDisponivel, BigDecimal estoqueMinimo, boolean abaixoDoMinimo,
                             BigDecimal custoMedio, List<LoteDetalhe> lotesEmOrdemDeSaida) {

    public record LoteDetalhe(String codigoLote, LocalDate dataValidade, LocalDate dataEntrada,
                              BigDecimal quantidade, CriterioBaixa criterio, int ordemDeSaida,
                              Long diasParaVencer) {
    }
}
