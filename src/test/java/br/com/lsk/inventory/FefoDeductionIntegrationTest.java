package br.com.lsk.inventory;

import br.com.lsk.inventory.application.dto.DeductedBatch;
import br.com.lsk.inventory.application.service.StockDeductionService;
import br.com.lsk.inventory.domain.exception.InsufficientStockException;
import br.com.lsk.inventory.domain.model.Category;
import br.com.lsk.inventory.domain.model.DeductionCriterion;
import br.com.lsk.inventory.domain.model.Batch;
import br.com.lsk.inventory.domain.model.Product;
import br.com.lsk.inventory.domain.repository.CategoryRepository;
import br.com.lsk.inventory.domain.repository.BatchRepository;
import br.com.lsk.inventory.domain.repository.ProductRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises fn_deduct_stock_fefo end to end against a real PostgreSQL (requires Docker). */
@SpringBootTest(properties = {
        "lsk.jobs.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
@Transactional
class FefoDeductionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired BatchRepository batches;
    @Autowired StockDeductionService deduction;
    @Autowired JdbcTemplate jdbc;

    @Test
    void picksTheEarliestExpiryFirstRegardlessOfReceiptOrder() {
        Product p = product("FEFO-1", true);
        batch(p, "A", expiresIn(30), receivedDaysAgo(3), "10");
        batch(p, "B", expiresIn(10), receivedDaysAgo(2), "5");

        List<DeductedBatch> r = deduction.deductStock(p, new BigDecimal("12"), "REF-1", "SALE", new BigDecimal("10"), "PED-1");

        assertThat(r).extracting(DeductedBatch::batchCode).containsExactly("B", "A");
        assertThat(r.get(0).quantity()).isEqualByComparingTo("5");
        assertThat(r.get(1).quantity()).isEqualByComparingTo("7");
        assertThat(r).allMatch(l -> l.criterion() == DeductionCriterion.FEFO);
        assertThat(batchBalance(p, "B")).isEqualByComparingTo("0");
        assertThat(batchStatus(p, "B")).isEqualTo("DEPLETED");
        assertThat(batchBalance(p, "A")).isEqualByComparingTo("3");
        assertThat(movementCount("REF-1")).isEqualTo(2);
    }

    @Test
    void fallsBackToFifoWhenBatchesHaveNoExpiry() {
        Product p = product("FIFO-1", false);
        batch(p, "X", null, receivedDaysAgo(10), "4");
        batch(p, "Y", null, receivedDaysAgo(5), "4");

        List<DeductedBatch> r = deduction.deductStock(p, new BigDecimal("6"), "REF-2", "SALE", null, null);

        assertThat(r).extracting(DeductedBatch::batchCode).containsExactly("X", "Y");
        assertThat(r).allMatch(l -> l.criterion() == DeductionCriterion.FIFO);
        assertThat(batchBalance(p, "Y")).isEqualByComparingTo("2");
    }

    @Test
    void batchesWithExpiryLeaveBeforeBatchesWithout() {
        Product p = product("MIX-1", false);
        batch(p, "NO-EXPIRY", null, receivedDaysAgo(30), "10");
        batch(p, "WITH-EXPIRY", expiresIn(20), receivedDaysAgo(1), "10");

        List<DeductedBatch> r = deduction.deductStock(p, new BigDecimal("12"), "REF-3", "SALE", null, null);

        assertThat(r).extracting(DeductedBatch::batchCode).containsExactly("WITH-EXPIRY", "NO-EXPIRY");
        assertThat(r.get(0).criterion()).isEqualTo(DeductionCriterion.FEFO);
        assertThat(r.get(1).criterion()).isEqualTo(DeductionCriterion.FIFO);
    }

    @Test
    void expiredBatchIsNeverPicked() {
        Product p = product("EXPIRED-1", true);
        batch(p, "EXPIRED", expiresIn(-1), receivedDaysAgo(60), "100");
        batch(p, "OK", expiresIn(5), receivedDaysAgo(1), "3");

        assertThatThrownBy(() -> deduction.deductStock(p, new BigDecimal("5"), "REF-4", "SALE", null, null))
                .isInstanceOf(InsufficientStockException.class);
    }

    private Product product(String sku, boolean controlsExpiry) {
        Category c = categories.save(new Category("Cat-" + sku, null));
        Product p = new Product();
        p.setSku(sku);
        p.setDescription("Product " + sku);
        p.setCategory(c);
        p.setControlsExpiry(controlsExpiry);
        p.setAverageCost(new BigDecimal("5"));
        p.setSalePrice(new BigDecimal("10"));
        return products.saveAndFlush(p);
    }

    private void batch(Product p, String code, LocalDate expiry, OffsetDateTime received, String qty) {
        batches.saveAndFlush(Batch.builder()
                .product(p).batchCode(code).expiryDate(expiry).receivedAt(received)
                .initialQuantity(new BigDecimal(qty)).currentQuantity(new BigDecimal(qty))
                .unitCost(new BigDecimal("5")).build());
    }

    private static LocalDate expiresIn(int days) {
        return LocalDate.now().plusDays(days);
    }

    private static OffsetDateTime receivedDaysAgo(int days) {
        return OffsetDateTime.now().minusDays(days);
    }

    private BigDecimal batchBalance(Product p, String code) {
        return jdbc.queryForObject("SELECT current_quantity FROM batches WHERE product_id = ? AND batch_code = ?",
                BigDecimal.class, p.getId(), code);
    }

    private String batchStatus(Product p, String code) {
        return jdbc.queryForObject("SELECT status FROM batches WHERE product_id = ? AND batch_code = ?",
                String.class, p.getId(), code);
    }

    private Integer movementCount(String reference) {
        return jdbc.queryForObject("SELECT count(*) FROM movements WHERE reference = ?", Integer.class, reference);
    }
}
