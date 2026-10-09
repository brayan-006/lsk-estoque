package br.com.lsk.estoque.domain.exception;

import java.math.BigDecimal;

public class EstoqueInsuficienteException extends RegraDeNegocioException {
    public EstoqueInsuficienteException(String sku, BigDecimal solicitado) {
        super("Estoque insuficiente para o SKU " + sku + " (solicitado: " + solicitado.stripTrailingZeros().toPlainString() + ")");
    }
}
