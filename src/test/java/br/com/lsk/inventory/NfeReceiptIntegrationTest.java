package br.com.lsk.inventory;

import br.com.lsk.inventory.application.dto.NfeReceiptCommand;
import br.com.lsk.inventory.application.dto.NfeReceiptCommand.ReceiptItem;
import br.com.lsk.inventory.application.dto.ReceiptResult;
import br.com.lsk.inventory.application.service.StockReceiptService;
import br.com.lsk.inventory.domain.exception.DuplicateOperationException;
import br.com.lsk.inventory.domain.exception.ResourceNotFoundException;
import br.com.lsk.inventory.domain.exception.BusinessRuleException;
import br.com.lsk.inventory.domain.model.Category;
import br.com.lsk.inventory.domain.model.Batch;
import br.com.lsk.inventory.domain.model.Product;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** NF-e receipt: batch, movement, average cost, idempotency and validations (requires Docker). */
@SpringBootTest(properties = {
        "lsk.jobs.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
@Transactional
class NfeReceiptIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired BatchRepository batches;
    @Autowired StockReceiptService receipt;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void createsBatchAndInboundMovement() {
        Product p = product("ENT-1", true);

        ReceiptResult r = receipt.registerNfe(nfe("KEY-1", item("ENT-1", "L1", "10", "8", today(30))));

        assertThat(r.reference()).isEqualTo("NFE-KEY-1");
        assertThat(r.itemsProcessed()).isEqualTo(1);
        assertThat(r.batches().get(0).skuBalance()).isEqualByComparingTo("10");
        assertThat(batchBalance(p, "L1")).isEqualByComparingTo("10");
        assertThat(movements("NFE-KEY-1")).isEqualTo(1);
        assertThat(p.getAverageCost()).isEqualByComparingTo("8");
    }

    @Test
    void sameAccessKeyIsNotReceivedTwice() {
        product("ENT-2", true);
        NfeReceiptCommand cmd = nfe("KEY-2", item("ENT-2", "L1", "10", "8", today(30)));

        receipt.registerNfe(cmd);

        assertThatThrownBy(() -> receipt.registerNfe(cmd)).isInstanceOf(DuplicateOperationException.class);
        assertThat(movements("NFE-KEY-2")).isEqualTo(1);
    }

    @Test
    void calculateWeightedAverageCost() {
        Product p = product("ENT-3", true);
        batch(p, "VELHO", today(60), "10");

        receipt.registerNfe(nfe("KEY-3", item("ENT-3", "NEW", "10", "7", today(90))));

        // (10 x 5 + 10 x 7) / 20 = 6
        assertThat(p.getAverageCost()).isEqualByComparingTo("6");
    }

    @Test
    void sameBatchCodeAddsToQuantity() {
        Product p = product("ENT-4", true);
        LocalDate expiry = today(30);
        batch(p, "A", expiry, "10");

        receipt.registerNfe(nfe("KEY-4", item("ENT-4", "A", "5", "5", expiry)));

        assertThat(batchBalance(p, "A")).isEqualByComparingTo("15");
    }

    @Test
    void rejectsAlreadyExpiredBatch() {
        product("ENT-5", true);

        assertThatThrownBy(() -> receipt.registerNfe(nfe("KEY-5", item("ENT-5", "L1", "10", "8", today(-1)))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void requiresExpiryWhenProductControlsIt() {
        product("ENT-6", true);

        assertThatThrownBy(() -> receipt.registerNfe(nfe("KEY-6", item("ENT-6", "L1", "10", "8", null))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsZeroOrNegativeQuantity() {
        product("ENT-7", false);

        assertThatThrownBy(() -> receipt.registerNfe(nfe("KEY-7", item("ENT-7", "L1", "0", "8", null))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsUnknownSku() {
        assertThatThrownBy(() -> receipt.registerNfe(nfe("KEY-8", item("MISSING-SKU", "L1", "1", "8", null))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static NfeReceiptCommand nfe(String key, ReceiptItem... items) {
        return new NfeReceiptCommand(key, "123", List.of(items));
    }

    private static ReceiptItem item(String sku, String batch, String qty, String cost, LocalDate expiry) {
        return new ReceiptItem(sku, batch, new BigDecimal(qty), new BigDecimal(cost), null, expiry);
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

    private void batch(Product p, String code, LocalDate expiry, String qty) {
        batches.saveAndFlush(Batch.builder()
                .product(p).batchCode(code).expiryDate(expiry).receivedAt(OffsetDateTime.now().minusDays(1))
                .initialQuantity(new BigDecimal(qty)).currentQuantity(new BigDecimal(qty))
                .unitCost(new BigDecimal("5")).build());
    }

    private static LocalDate today(int plusDays) {
        return LocalDate.now().plusDays(plusDays);
    }

    private BigDecimal batchBalance(Product p, String code) {
        em.flush();
        return jdbc.queryForObject("SELECT current_quantity FROM batches WHERE product_id = ? AND batch_code = ?",
                BigDecimal.class, p.getId(), code);
    }

    private Integer movements(String reference) {
        em.flush();
        return jdbc.queryForObject("SELECT count(*) FROM movements WHERE reference = ?", Integer.class, reference);
    }
}
