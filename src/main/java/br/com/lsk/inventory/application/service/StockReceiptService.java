package br.com.lsk.inventory.application.service;

import br.com.lsk.inventory.application.dto.NfeReceiptCommand;
import br.com.lsk.inventory.application.dto.NfeReceiptCommand.ReceiptItem;
import br.com.lsk.inventory.application.dto.ReceiptResult;
import br.com.lsk.inventory.application.event.MovementRecordedEvent;
import br.com.lsk.inventory.domain.exception.DuplicateOperationException;
import br.com.lsk.inventory.domain.exception.ResourceNotFoundException;
import br.com.lsk.inventory.domain.exception.BusinessRuleException;
import br.com.lsk.inventory.domain.model.Batch;
import br.com.lsk.inventory.domain.model.Movement;
import br.com.lsk.inventory.domain.model.Product;
import br.com.lsk.inventory.domain.model.BatchStatus;
import br.com.lsk.inventory.domain.model.MovementType;
import br.com.lsk.inventory.domain.repository.IdempotencyRepository;
import br.com.lsk.inventory.domain.repository.BatchRepository;
import br.com.lsk.inventory.domain.repository.MovementRepository;
import br.com.lsk.inventory.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockReceiptService {

    private final IdempotencyRepository idempotency;
    private final ProductRepository products;
    private final BatchRepository batches;
    private final MovementRepository movements;
    private final MinimumStockService minimumStock;
    private final ApplicationEventPublisher events;

    @Transactional
    public ReceiptResult registerNfe(NfeReceiptCommand cmd) {
        String reference = "NFE-" + cmd.accessKey();
        if (!idempotency.register(reference, "NFE")) {
            throw new DuplicateOperationException("NF-e already received: " + cmd.accessKey());
        }

        var items = cmd.items().stream()
                .sorted(Comparator.comparing(ReceiptItem::sku).thenComparing(ReceiptItem::batchCode))
                .toList();

        List<ReceiptResult.BatchReceipt> result = new ArrayList<>();
        Map<String, Product> affected = new LinkedHashMap<>();

        for (ReceiptItem item : items) {
            validateItem(item);
            Product product = affected.computeIfAbsent(item.sku(), sku -> products.fetchForUpdate(sku)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + sku)));
            if (!product.isActive()) {
                throw new BusinessRuleException("Inactive product: " + item.sku());
            }
            validateExpiry(product, item);

            BigDecimal balanceBefore = batches.availableBalance(product.getId());

            Batch batch = batches.fetchForUpdate(product.getId(), item.batchCode())
                    .map(existing -> accumulate(existing, item))
                    .orElseGet(() -> batches.save(Batch.builder()
                            .product(product)
                            .batchCode(item.batchCode())
                            .manufacturingDate(item.manufacturingDate())
                            .expiryDate(item.expiryDate())
                            .initialQuantity(item.quantity())
                            .currentQuantity(item.quantity())
                            .unitCost(item.unitCost())
                            .nfeKey(cmd.accessKey())
                            .build()));

            product.setAverageCost(newAverageCost(product.getAverageCost(), balanceBefore, item));

            movements.save(Movement.builder()
                    .product(product)
                    .batch(batch)
                    .type(MovementType.INBOUND)
                    .quantity(item.quantity())
                    .unitCost(item.unitCost())
                    .reference(reference)
                    .source("NFE")
                    .sourceDocument(cmd.number())
                    .build());

            result.add(new ReceiptResult.BatchReceipt(item.sku(), item.batchCode(), item.quantity(),
                    balanceBefore.add(item.quantity())));
        }

        affected.values().forEach(minimumStock::evaluate);
        events.publishEvent(new MovementRecordedEvent(reference, MovementType.INBOUND));
        log.info("NF-e {} received: {} item(s)", cmd.accessKey(), result.size());
        return new ReceiptResult(reference, result.size(), result);
    }

    private void validateItem(ReceiptItem item) {
        if (item.quantity() == null || item.quantity().signum() <= 0) {
            throw new BusinessRuleException("Quantity must be greater than zero (SKU " + item.sku() + ")");
        }
        if (item.unitCost() == null || item.unitCost().signum() < 0) {
            throw new BusinessRuleException("Invalid unit cost (SKU " + item.sku() + ")");
        }
    }

    private void validateExpiry(Product product, ReceiptItem item) {
        if (item.expiryDate() == null && product.isControlsExpiry()) {
            throw new BusinessRuleException("SKU %s controls expiry: expiryDate is required for batch %s"
                    .formatted(item.sku(), item.batchCode()));
        }
        if (item.expiryDate() != null && item.expiryDate().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("Batch %s of SKU %s is already expired (%s)"
                    .formatted(item.batchCode(), item.sku(), item.expiryDate()));
        }
        if (item.manufacturingDate() != null && item.expiryDate() != null
                && item.expiryDate().isBefore(item.manufacturingDate())) {
            throw new BusinessRuleException("Expiry before manufacturing date in batch " + item.batchCode());
        }
    }

    /** Same batch code already exists for the product: adds to the existing quantity. */
    private Batch accumulate(Batch batch, ReceiptItem item) {
        if (batch.getStatus() == BatchStatus.EXPIRED || batch.getStatus() == BatchStatus.BLOCKED) {
            throw new BusinessRuleException("Batch %s of SKU %s is %s and cannot receive stock"
                    .formatted(batch.getBatchCode(), item.sku(), batch.getStatus()));
        }
        if (item.expiryDate() != null && batch.getExpiryDate() != null
                && !item.expiryDate().equals(batch.getExpiryDate())) {
            throw new BusinessRuleException("Batch %s already exists with a different expiry (%s)"
                    .formatted(batch.getBatchCode(), batch.getExpiryDate()));
        }
        batch.setInitialQuantity(batch.getInitialQuantity().add(item.quantity()));
        batch.setCurrentQuantity(batch.getCurrentQuantity().add(item.quantity()));
        batch.setStatus(BatchStatus.ACTIVE);
        return batch;
    }

    /** Weighted average cost. */
    private BigDecimal newAverageCost(BigDecimal currentCost, BigDecimal balanceBefore, ReceiptItem item) {
        BigDecimal valueBefore = balanceBefore.multiply(currentCost);
        BigDecimal receiptValue = item.quantity().multiply(item.unitCost());
        BigDecimal newBalance = balanceBefore.add(item.quantity());
        return valueBefore.add(receiptValue).divide(newBalance, 4, RoundingMode.HALF_UP);
    }
}
