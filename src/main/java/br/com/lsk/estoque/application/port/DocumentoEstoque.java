package br.com.lsk.estoque.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Documento de estoque neutro (independente de ERP). */
public record DocumentoEstoque(String referencia, LocalDate data, String comentario, List<Linha> linhas) {

    public record Linha(String itemCode, BigDecimal quantidade, BigDecimal custoUnitario, List<Lote> lotes) {
    }

    public record Lote(String numero, BigDecimal quantidade, LocalDate fabricacao, LocalDate validade) {
    }
}
