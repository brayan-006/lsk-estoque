package br.com.lsk.inventory.application.service;

import br.com.lsk.inventory.application.dto.DeductedBatch;
import br.com.lsk.inventory.domain.model.Product;
import br.com.lsk.inventory.domain.repository.FefoDeductionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** FEFO (with FIFO fallback) lives only in fn_deduct_stock_fefo. Requires an open transaction. */
@Service
@RequiredArgsConstructor
public class StockDeductionService {

    private final FefoDeductionRepository fefoDeduction;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<DeductedBatch> deductStock(Product product, BigDecimal quantity, String reference,
                                      String source, BigDecimal salePrice, String sourceDocument) {
        return fefoDeduction.execute(product, quantity, reference, source, salePrice, sourceDocument);
    }
}
