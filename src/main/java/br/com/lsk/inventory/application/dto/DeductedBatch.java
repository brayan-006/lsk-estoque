package br.com.lsk.inventory.application.dto;

import br.com.lsk.inventory.domain.model.DeductionCriterion;

import java.math.BigDecimal;

/** Row returned by fn_deduct_stock_fefo. */
public record DeductedBatch(Long movementId, Long batchId, String batchCode, BigDecimal quantity,
                          BigDecimal unitCost, DeductionCriterion criterion) {
}
