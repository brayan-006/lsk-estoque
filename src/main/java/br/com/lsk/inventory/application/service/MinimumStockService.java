package br.com.lsk.inventory.application.service;

import br.com.lsk.inventory.application.event.StockAlertEvent;
import br.com.lsk.inventory.domain.model.StockAlert;
import br.com.lsk.inventory.domain.model.Product;
import br.com.lsk.inventory.domain.model.AlertType;
import br.com.lsk.inventory.domain.repository.StockAlertRepository;
import br.com.lsk.inventory.domain.repository.BatchRepository;
import br.com.lsk.inventory.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinimumStockService {

    private final BatchRepository batches;
    private final StockAlertRepository alerts;
    private final ProductRepository products;
    private final ApplicationEventPublisher events;

    /**
     * Keeps one alert per product: opens it when the balance is at or below the minimum, keeps its figures
     * up to date while it stays open, and closes it when the balance recovers.
     */
    @Transactional
    public void evaluate(Product product) {
        BigDecimal minimum = product.getMinimumStock();
        if (minimum == null || minimum.signum() <= 0) {
            return;
        }
        BigDecimal balance = batches.availableBalance(product.getId());

        if (balance.compareTo(minimum) <= 0) {
            String message = "Minimum stock reached: SKU %s has balance %s (minimum %s)".formatted(
                    product.getSku(), balance.stripTrailingZeros().toPlainString(),
                    minimum.stripTrailingZeros().toPlainString());

            var open = alerts.findFirstByProductIdAndTypeAndResolvedFalse(product.getId(), AlertType.MINIMUM_STOCK);
            if (open.isPresent()) {
                StockAlert alert = open.get();
                alert.setBalanceAtAlert(balance);
                alert.setMinimumStock(minimum);
                alert.setMessage(message);
                return;
            }

            StockAlert alert = alerts.save(StockAlert.builder()
                    .product(product)
                    .type(AlertType.MINIMUM_STOCK)
                    .balanceAtAlert(balance)
                    .minimumStock(minimum)
                    .message(message)
                    .build());
            log.warn(message);
            events.publishEvent(new StockAlertEvent(alert.getId(), product.getSku(), balance, minimum,
                    message, alert.getCreatedAt()));
        } else {
            alerts.resolveOpen(product.getId(), AlertType.MINIMUM_STOCK, OffsetDateTime.now());
        }
    }

    /** Periodic sweep: catches balances that dropped because a batch expired. */
    public int evaluateAll() {
        var below = products.findBelowMinimum();
        below.forEach(this::evaluate);
        return below.size();
    }
}