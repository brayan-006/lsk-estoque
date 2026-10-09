package br.com.lsk.estoque.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record ResultadoEntrada(String referencia, int itensProcessados, List<LoteEntrada> lotes) {

    public record LoteEntrada(String sku, String codigoLote, BigDecimal quantidade, BigDecimal saldoDoSku) {
    }
}
