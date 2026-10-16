package br.com.lsk.inventory.web.dto;

import br.com.lsk.inventory.application.dto.NfeReceiptCommand;
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

public record NfeReceiptRequest(
        @NotBlank @Pattern(regexp = "\\d{44}", message = "accessKey must have 44 digits") String accessKey,
        @Size(max = 60) String number,
        @NotEmpty List<@Valid Item> items) {

    public record Item(
            @NotBlank String sku,
            @NotBlank @Size(max = 60) String batchCode,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @PositiveOrZero BigDecimal unitCost,
            LocalDate manufacturingDate,
            LocalDate expiryDate) {
    }

    public NfeReceiptCommand toCommand() {
        return new NfeReceiptCommand(accessKey, number, items.stream()
                .map(i -> new NfeReceiptCommand.ReceiptItem(i.sku().trim(), i.batchCode().trim(), i.quantity(),
                        i.unitCost(), i.manufacturingDate(), i.expiryDate()))
                .toList());
    }
}
