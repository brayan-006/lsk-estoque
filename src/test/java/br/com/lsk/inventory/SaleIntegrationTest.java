package br.com.lsk.inventory;

import br.com.lsk.inventory.application.dto.NfeReceiptCommand;
import br.com.lsk.inventory.application.dto.NfeReceiptCommand.ReceiptItem;
import br.com.lsk.inventory.application.dto.SaleEvent;
import br.com.lsk.inventory.application.dto.SaleEvent.SaleItem;
import br.com.lsk.inventory.application.service.StockReceiptService;
import br.com.lsk.inventory.application.service.ProcessSaleService;
import br.com.lsk.inventory.application.service.ProcessSaleService.Result;
import br.com.lsk.inventory.domain.exception.InsufficientStockException;
import br.com.lsk.inventory.domain.exception.ResourceNotFoundException;
import br.com.lsk.inventory.domain.model.Category;
import br.com.lsk.inventory.domain.model.Batch;
import br.com.lsk.inventory.domain.model.Product;
import br.com.lsk.inventory.domain.model.AlertType;
import br.com.lsk.inventory.domain.repository.StockAlertRepository;
import br.com.lsk.inventory.domain.repository.CategoryRepository;
import br.com.lsk.inventory.domain.repository.BatchRepository;
import br.com.lsk.inventory.domain.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sale processing (idempotency, business errors) and minimum stock (requires Docker). */
@SpringBootTest(properties = {
        "lsk.jobs.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
@Transactional
class SaleIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired BatchRepository batches;
    @Autowired StockAlertRepository alerts;
    @Autowired ProcessSaleService sales;
    @Autowired StockReceiptService receipt;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void sameEventIdNeverDeductsTwice() {
        Product p = product("VND-1", "0");
        batch(p, "L1", 10);
        SaleEvent event = sale("VND-1", "3");

        assertThat(sales.process(event)).isEqualTo(Result.PROCESSED);
        assertThat(sales.process(event)).isEqualTo(Result.DUPLICATE);

        assertThat(balance(p)).isEqualByComparingTo("7");
        assertThat(movements("SALE-" + event.eventId())).isEqualTo(1);
    }

    @Test
    void insufficientStockIsRejected() {
        Product p = product("VND-2", "0");
        batch(p, "L1", 2);

        assertThatThrownBy(() -> sales.process(sale("VND-2", "5")))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void unknownSkuIsRejected() {
        assertThatThrownBy(() -> sales.process(sale("MISSING-SKU", "1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void alertOpensAtMinimumStaysSingleAndClosesOnReceipt() {
        Product p = product("VND-3", "5");
        batch(p, "L1", 10);

        sales.process(sale("VND-3", "5")); // balance 5 (= minimum)
        assertThat(alertOpen(p)).isTrue();
        assertThat(totalAlerts(p)).isEqualTo(1);

        sales.process(sale("VND-3", "1")); // balance 4: still a single alert
        assertThat(totalAlerts(p)).isEqualTo(1);

        receipt.registerNfe(new NfeReceiptCommand("KEY-VND-3", "1", List.of(
                new ReceiptItem("VND-3", "L2", new BigDecimal("10"), new BigDecimal("5"), null,
                        LocalDate.now().plusDays(60)))));
        assertThat(alertOpen(p)).isFalse();
    }

    @Test
    void noAlertWhileAboveMinimum() {
        Product p = product("VND-4", "5");
        batch(p, "L1", 10);

        sales.process(sale("VND-4", "2")); // balance 8

        assertThat(totalAlerts(p)).isZero();
    }

    private static SaleEvent sale(String sku, String qty) {
        return new SaleEvent(UUID.randomUUID(), "PED-" + UUID.randomUUID(), OffsetDateTime.now(),
                List.of(new SaleItem(sku, new BigDecimal(qty), new BigDecimal("10"))));
    }

    private Product product(String sku, String minimum) {
        Category c = categories.save(new Category("Cat-" + sku, null));
        Product p = new Product();
        p.setSku(sku);
        p.setDescription("Product " + sku);
        p.setCategory(c);
        p.setControlsExpiry(false);
        p.setAverageCost(new BigDecimal("5"));
        p.setSalePrice(new BigDecimal("10"));
        p.setMinimumStock(new BigDecimal(minimum));
        return products.saveAndFlush(p);
    }

    private void batch(Product p, String code, int qty) {
        batches.saveAndFlush(Batch.builder()
                .product(p).batchCode(code).receivedAt(OffsetDateTime.now().minusDays(1))
                .initialQuantity(new BigDecimal(qty)).currentQuantity(new BigDecimal(qty))
                .unitCost(new BigDecimal("5")).build());
    }

    private BigDecimal balance(Product p) {
        em.flush();
        return batches.availableBalance(p.getId());
    }

    private boolean alertOpen(Product p) {
        em.flush();
        return alerts.existsByProductIdAndTypeAndResolvedFalse(p.getId(), AlertType.MINIMUM_STOCK);
    }

    private long totalAlerts(Product p) {
        em.flush();
        return alerts.findAll().stream().filter(a -> a.getProduct().getId().equals(p.getId())).count();
    }

    private Integer movements(String reference) {
        em.flush();
        return jdbc.queryForObject("SELECT count(*) FROM movements WHERE reference = ?", Integer.class, reference);
    }
}
