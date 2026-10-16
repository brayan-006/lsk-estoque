package br.com.lsk.inventory.web.dto;

import br.com.lsk.inventory.application.service.ProductService;
import br.com.lsk.inventory.web.dto.DashboardViews.MovementView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class OperationsDtos {

    private OperationsDtos() {
    }

    public record SaleRequest(UUID idempotencyKey, @Size(max = 60) String orderRef,
                              @NotEmpty List<@Valid Item> items) {

        public record Item(@NotBlank String sku, @NotNull @Positive BigDecimal quantity,
                           @PositiveOrZero BigDecimal unitPrice) {
        }
    }

    public record SaleResponse(String reference, String status, List<MovementView> movements) {
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "sku may only contain letters, digits, '.', '_' and '-'") String sku,
            @NotBlank @Size(max = 200) String description,
            @NotBlank @Size(max = 80) String category,
            @NotBlank @Size(max = 10) String unit,
            @NotNull @PositiveOrZero BigDecimal minimumStock,
            @NotNull @PositiveOrZero BigDecimal salePrice,
            Boolean controlsExpiry,
            @Size(max = 50) String sapItemCode) {

        public ProductService.CreateCommand toCommand() {
            return new ProductService.CreateCommand(sku, description, category, unit, minimumStock, salePrice,
                    controlsExpiry == null || controlsExpiry, sapItemCode);
        }
    }

    public record UpdateProductRequest(
            @NotBlank @Size(max = 200) String description,
            @NotBlank @Size(max = 10) String unit,
            @NotNull @PositiveOrZero BigDecimal minimumStock,
            @NotNull @PositiveOrZero BigDecimal salePrice) {

        public ProductService.UpdateCommand toCommand() {
            return new ProductService.UpdateCommand(description, unit, minimumStock, salePrice);
        }
    }

    public record RetryResponse(String reference, List<MovementView> movements) {
    }

    public record SystemStatus(boolean sapEnabled) {
    }
}
