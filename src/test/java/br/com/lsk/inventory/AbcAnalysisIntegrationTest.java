package br.com.lsk.inventory;

import br.com.lsk.inventory.application.service.StockDeductionService;
import br.com.lsk.inventory.application.service.AbcAnalysisService;
import br.com.lsk.inventory.domain.model.Category;
import br.com.lsk.inventory.domain.model.AbcClass;
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
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** ABC by revenue: A up to 80 %, B up to 95 %, C the rest and items without sales (requires Docker). */
@SpringBootTest(properties = {
        "lsk.jobs.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
@Transactional
class AbcAnalysisIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired BatchRepository batches;
    @Autowired StockDeductionService deduction;
    @Autowired AbcAnalysisService abcAnalysis;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void ranksByRevenueAndDowngradesItemsWithoutSales() {
        // revenue: P1 = 1000, P2 = 300, P3 = 100, P4 = 10 (total 1410)
        Product p1 = sold("ABC-1", 10, "100");
        Product p2 = sold("ABC-2", 3, "100");
        Product p3 = sold("ABC-3", 1, "100");
        Product p4 = sold("ABC-4", 1, "10");
        Product p5 = product("ABC-5");
        p5.setAbcClass(AbcClass.A); // old class, no sales in the window
        products.saveAndFlush(p5);

        abcAnalysis.recalculate();

        assertThat(abcClass(p1)).isEqualTo("A"); // 0 % accumulated before it
        assertThat(abcClass(p2)).isEqualTo("A"); // ~70.9 % before it (< 80 %)
        assertThat(abcClass(p3)).isEqualTo("B"); // ~92.2 % before it (< 95 %)
        assertThat(abcClass(p4)).isEqualTo("C"); // ~99.3 % before it
        assertThat(abcClass(p5)).isEqualTo("C"); // no sales: downgraded
    }

    private Product sold(String sku, int qty, String price) {
        Product p = product(sku);
        batches.saveAndFlush(Batch.builder()
                .product(p).batchCode("L1").receivedAt(OffsetDateTime.now().minusDays(1))
                .initialQuantity(new BigDecimal("100")).currentQuantity(new BigDecimal("100"))
                .unitCost(new BigDecimal("5")).build());
        deduction.deductStock(p, new BigDecimal(qty), "SALE-" + sku, "SALE", new BigDecimal(price), "PED-" + sku);
        return p;
    }

    private Product product(String sku) {
        Category c = categories.save(new Category("Cat-" + sku, null));
        Product p = new Product();
        p.setSku(sku);
        p.setDescription("Product " + sku);
        p.setCategory(c);
        p.setControlsExpiry(false);
        p.setAverageCost(new BigDecimal("5"));
        p.setSalePrice(new BigDecimal("10"));
        return products.saveAndFlush(p);
    }

    private String abcClass(Product p) {
        em.flush();
        return jdbc.queryForObject("SELECT abc_class FROM products WHERE id = ?", String.class, p.getId()).trim();
    }
}
