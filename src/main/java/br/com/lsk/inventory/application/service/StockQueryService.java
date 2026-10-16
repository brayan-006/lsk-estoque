package br.com.lsk.inventory.application.service;

import br.com.lsk.inventory.application.dto.StockDetail;
import br.com.lsk.inventory.application.dto.StockDetail.BatchDetail;
import br.com.lsk.inventory.application.dto.ProductAbcView;
import br.com.lsk.inventory.domain.exception.ResourceNotFoundException;
import br.com.lsk.inventory.domain.model.AbcClass;
import br.com.lsk.inventory.domain.model.DeductionCriterion;
import br.com.lsk.inventory.domain.model.Batch;
import br.com.lsk.inventory.domain.model.Product;
import br.com.lsk.inventory.domain.repository.BatchRepository;
import br.com.lsk.inventory.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockQueryService {

    private final ProductRepository products;
    private final BatchRepository batches;

    @Transactional(readOnly = true)
    public StockDetail query(String sku) {
        Product product = products.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + sku));

        List<Batch> available = batches.findAvailableInFefoOrder(product.getId());
        LocalDate today = LocalDate.now();

        List<BatchDetail> details = new ArrayList<>();
        BigDecimal balance = BigDecimal.ZERO;
        int order = 1;
        for (Batch batch : available) {
            balance = balance.add(batch.getCurrentQuantity());
            details.add(new BatchDetail(
                    batch.getBatchCode(),
                    batch.getExpiryDate(),
                    batch.getReceivedAt().toLocalDate(),
                    batch.getCurrentQuantity(),
                    batch.getExpiryDate() == null ? DeductionCriterion.FIFO : DeductionCriterion.FEFO,
                    order++,
                    batch.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(today, batch.getExpiryDate())));
        }

        boolean below = product.getMinimumStock().signum() > 0 && balance.compareTo(product.getMinimumStock()) <= 0;
        return new StockDetail(product.getSku(), product.getDescription(), product.getUnit(),
                product.getAbcClass(), balance, product.getMinimumStock(), below, product.getAverageCost(), details);
    }

    @Transactional(readOnly = true)
    public List<ProductAbcView> listByClass(AbcClass abcClass) {
        List<Product> list = products.findByAbcClassAndActiveTrueOrderBySkuAsc(abcClass);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> balances = batches.balancesByProduct(list.stream().map(Product::getId).toList())
                .stream()
                .collect(Collectors.toMap(BatchRepository.ProductBalance::getProductId, BatchRepository.ProductBalance::getBalance));

        return list.stream()
                .map(p -> new ProductAbcView(p.getSku(), p.getDescription(), p.getAbcClass(), p.getSalePrice(),
                        balances.getOrDefault(p.getId(), BigDecimal.ZERO)))
                .toList();
    }
}
