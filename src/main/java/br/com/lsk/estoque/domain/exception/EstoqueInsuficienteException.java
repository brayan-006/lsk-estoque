package br.com.lsk.estoque.domain.exception;

import java.math.BigDecimal;

public class EstoqueInsuficienteException extends RegraDeNegocioException {
    public EstoqueInsuficienteException(String sku, BigDecimal solicitado) {
        super("Insufficient stock for SKU " + sku + " (requested: " + solicitado.stripTrailingZeros().toPlainString() + ")");
    }
}
