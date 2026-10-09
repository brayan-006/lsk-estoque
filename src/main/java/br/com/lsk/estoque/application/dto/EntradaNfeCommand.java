package br.com.lsk.estoque.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EntradaNfeCommand(String chaveAcesso, String numero, List<ItemEntrada> itens) {

    public record ItemEntrada(String sku, String codigoLote, BigDecimal quantidade, BigDecimal custoUnitario,
                              LocalDate dataFabricacao, LocalDate dataValidade) {
    }
}
