package br.com.lsk.inventory;

import br.com.lsk.inventory.application.service.ProductService;
import br.com.lsk.inventory.application.service.ProductService.CreateCommand;
import br.com.lsk.inventory.application.service.ProductService.UpdateCommand;
import br.com.lsk.inventory.domain.exception.DuplicateOperationException;
import br.com.lsk.inventory.domain.exception.ResourceNotFoundException;
import br.com.lsk.inventory.domain.exception.BusinessRuleException;
import br.com.lsk.inventory.domain.model.Batch;
import br.com.lsk.inventory.domain.model.Product;
import br.com.lsk.inventory.domain.model.AlertType;
import br.com.lsk.inventory.domain.repository.StockAlertRepository;
import br.com.lsk.inventory.domain.repository.CategoryRepository;
import br.com.lsk.inventory.domain.repository.BatchRepository;
import br.com.lsk.inventory.domain.repository.ProductRepository;
import br.com.lsk.inventory.web.dto.DashboardViews.ProductView;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Product registration and update (requires Docker). */
@SpringBootTest(properties = {
        "lsk.jobs.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
@Transactional
class OperationsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired BatchRepository batches;
    @Autowired StockAlertRepository alerts;
    @Autowired EntityManager em;

    @Test
    void createsProductAndCategory() {
        ProductView view = productService.create(command("OPS-1", "0"));

        assertThat(view.abcClass()).isEqualTo("C");
        assertThat(view.balance()).isEqualByComparingTo("0");
        assertThat(products.findBySku("OPS-1")).isPresent();
        assertThat(categories.findByName("Cat-OPS")).isPresent();
    }

    @Test
    void rejectsDuplicatedSku() {
        productService.create(command("OPS-2", "0"));

        assertThatThrownBy(() -> productService.create(command("OPS-2", "0")))
                .isInstanceOf(DuplicateOperationException.class);
    }

    @Test
    void updatingMinimumStockOpensAndClosesTheAlert() {
        productService.create(command("OPS-3", "0"));
        Product product = products.findBySku("OPS-3").orElseThrow();
        batches.saveAndFlush(Batch.builder()
                .product(product).batchCode("L1").receivedAt(OffsetDateTime.now().minusDays(1))
                .initialQuantity(new BigDecimal("10")).currentQuantity(new BigDecimal("10"))
                .unitCost(new BigDecimal("5")).build());

        ProductView raised = productService.update("OPS-3", update("10"));
        assertThat(raised.belowMinimum()).isTrue();
        assertThat(alerts.existsByProductIdAndTypeAndResolvedFalse(product.getId(), AlertType.MINIMUM_STOCK)).isTrue();

        ProductView lowered = productService.update("OPS-3", update("2"));
        assertThat(lowered.belowMinimum()).isFalse();
        em.flush();
        assertThat(alerts.existsByProductIdAndTypeAndResolvedFalse(product.getId(), AlertType.MINIMUM_STOCK)).isFalse();
    }

    @Test
    void updateFailsForUnknownSku() {
        assertThatThrownBy(() -> productService.update("DOES-NOT-EXIST", update("1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivatesProductWithoutStock() {
        productService.create(command("OPS-4", "0"));

        productService.deactivate("OPS-4");

        assertThat(products.findBySku("OPS-4").orElseThrow().isActive()).isFalse();
        assertThatThrownBy(() -> productService.update("OPS-4", update("1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void refusesToRemoveProductWithStock() {
        productService.create(command("OPS-5", "0"));
        Product product = products.findBySku("OPS-5").orElseThrow();
        batches.saveAndFlush(Batch.builder()
                .product(product).batchCode("L1").receivedAt(OffsetDateTime.now().minusDays(1))
                .initialQuantity(new BigDecimal("3")).currentQuantity(new BigDecimal("3"))
                .unitCost(new BigDecimal("5")).build());

        assertThatThrownBy(() -> productService.deactivate("OPS-5")).isInstanceOf(BusinessRuleException.class);
        assertThat(products.findBySku("OPS-5").orElseThrow().isActive()).isTrue();
    }

    @Test
    void creatingARemovedSkuReactivatesIt() {
        productService.create(command("OPS-6", "0"));
        productService.deactivate("OPS-6");

        ProductView view = productService.create(new CreateCommand("OPS-6", "New description", "Cat-OPS", "UN",
                new BigDecimal("1"), new BigDecimal("20"), false, null));

        assertThat(view.description()).isEqualTo("New description");
        assertThat(products.findBySku("OPS-6").orElseThrow().isActive()).isTrue();
    }

    private static CreateCommand command(String sku, String minimum) {
        return new CreateCommand(sku, "Test product " + sku, "Cat-OPS", "UN", new BigDecimal(minimum),
                new BigDecimal("10"), false, null);
    }

    private static UpdateCommand update(String minimum) {
        return new UpdateCommand("Updated description", "UN", new BigDecimal(minimum), new BigDecimal("12"));
    }
}
