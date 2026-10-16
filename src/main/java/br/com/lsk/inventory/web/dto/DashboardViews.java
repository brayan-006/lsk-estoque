package br.com.lsk.inventory.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class DashboardViews {

    private DashboardViews() {
    }

    public record AlertView(Long id, String sku, String description, String type, BigDecimal balance,
                            BigDecimal minimumStock, String message, OffsetDateTime createdAt) {
    }

    public record MovementView(Long id, String type, String sku, String batchCode, BigDecimal quantity,
                               String reference, String sapStatus, int sapAttempts, Long sapDocEntry,
                               String sapError, OffsetDateTime createdAt) {
    }

    public record ProductView(String sku, String description, String unit, String abcClass, BigDecimal balance,
                              BigDecimal minimumStock, BigDecimal salePrice, boolean belowMinimum) {
    }
}
