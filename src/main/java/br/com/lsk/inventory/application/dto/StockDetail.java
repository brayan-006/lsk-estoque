package br.com.lsk.inventory.application.dto;

import br.com.lsk.inventory.domain.model.AbcClass;
import br.com.lsk.inventory.domain.model.DeductionCriterion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StockDetail(String sku, String description, String unit, AbcClass abcClass,
                             BigDecimal availableBalance, BigDecimal minimumStock, boolean belowMinimum,
                             BigDecimal averageCost, List<BatchDetail> batchesInPickingOrder) {

    public record BatchDetail(String batchCode, LocalDate expiryDate, LocalDate receivedAt,
                              BigDecimal quantity, DeductionCriterion criterion, int pickingOrder,
                              Long daysUntilExpiry) {
    }
}
