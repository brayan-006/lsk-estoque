package br.com.lsk.inventory.application.service;

import br.com.lsk.inventory.application.dto.SaleEvent;
import br.com.lsk.inventory.application.event.MovementRecordedEvent;
import br.com.lsk.inventory.domain.exception.ResourceNotFoundException;
import br.com.lsk.inventory.domain.model.Product;
import br.com.lsk.inventory.domain.model.MovementType;
import br.com.lsk.inventory.domain.repository.IdempotencyRepository;
import br.com.lsk.inventory.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessSaleService {

    public enum Result {PROCESSED, DUPLICATE}

    private final IdempotencyRepository idempotency;
    private final ProductRepository products;
    private final StockDeductionService stockDeduction;
    private final MinimumStockService minimumStock;
    private final ApplicationEventPublisher events;

    /** One transaction per event: idempotency, FEFO for every item and the minimum-stock check. All or nothing. */
    @Transactional
    public Result process(SaleEvent event) {
        String reference = "SALE-" + event.eventId();

        if (!idempotency.register(reference, "SALE")) {
            log.warn("Sale event {} already processed; ignoring (idempotency)", event.eventId());
            return Result.DUPLICATE;
        }

        // fixed SKU order avoids deadlocks between concurrent events
        var items = event.items().stream()
                .sorted(Comparator.comparing(SaleEvent.SaleItem::sku))
                .toList();

        Map<String, Product> affected = new LinkedHashMap<>();
        for (SaleEvent.SaleItem item : items) {
            Product product = affected.computeIfAbsent(item.sku(), sku -> products.fetchForUpdate(sku)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + sku)));

            var price = item.unitPrice() != null ? item.unitPrice() : product.getSalePrice();
            var deducted = stockDeduction.deductStock(product, item.quantity(), reference, "SALE", price, event.order());
            log.info("Order {} | SKU {} | deducted {} from {} batch(es): {}", event.order(), item.sku(),
                    item.quantity(), deducted.size(), deducted);
        }

        affected.values().forEach(minimumStock::evaluate);
        events.publishEvent(new MovementRecordedEvent(reference, MovementType.OUTBOUND));
        return Result.PROCESSED;
    }
}
