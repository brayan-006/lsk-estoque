package br.com.lsk.inventory.domain.repository;

import br.com.lsk.inventory.application.dto.DeductedBatch;
import br.com.lsk.inventory.domain.model.Product;

import java.math.BigDecimal;
import java.util.List;

/** Port for the fn_deduct_stock_fefo function. */
public interface FefoDeductionRepository {

    /** Throws InsufficientStockException when the balance does not cover the quantity. */
    List<DeductedBatch> execute(Product product, BigDecimal quantity, String reference,
                               String source, BigDecimal salePrice, String sourceDocument);
}
