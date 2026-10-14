package br.com.lsk.estoque.web.dto;

import br.com.lsk.estoque.application.dto.EntradaNfeCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EntradaNfeRequest(
        @NotBlank @Pattern(regexp = "\\d{44}", message = "chaveAcesso must have 44 digits") String chaveAcesso,
        @Size(max = 60) String numero,
        @NotEmpty List<@Valid Item> itens) {

    public record Item(
            @NotBlank String sku,
            @NotBlank @Size(max = 60) String codigoLote,
            @NotNull @Positive BigDecimal quantidade,
            @NotNull @PositiveOrZero BigDecimal custoUnitario,
            LocalDate dataFabricacao,
            LocalDate dataValidade) {
    }

    public EntradaNfeCommand toCommand() {
        return new EntradaNfeCommand(chaveAcesso, numero, itens.stream()
                .map(i -> new EntradaNfeCommand.ItemEntrada(i.sku().trim(), i.codigoLote().trim(), i.quantidade(),
                        i.custoUnitario(), i.dataFabricacao(), i.dataValidade()))
                .toList());
    }
}
