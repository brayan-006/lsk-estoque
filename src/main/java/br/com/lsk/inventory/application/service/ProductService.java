package br.com.lsk.inventory.application.service;

import br.com.lsk.inventory.domain.exception.DuplicateOperationException;
import br.com.lsk.inventory.domain.exception.ResourceNotFoundException;
import br.com.lsk.inventory.domain.exception.BusinessRuleException;
import br.com.lsk.inventory.domain.model.Category;
import br.com.lsk.inventory.domain.model.Product;
import br.com.lsk.inventory.domain.repository.CategoryRepository;
import br.com.lsk.inventory.domain.repository.BatchRepository;
import br.com.lsk.inventory.domain.repository.ProductRepository;
import br.com.lsk.inventory.web.dto.DashboardViews.ProductView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductService {

    public record CreateCommand(String sku, String description, String category, String unit,
                                BigDecimal minimumStock, BigDecimal salePrice, boolean controlsExpiry,
                                String sapItemCode) {
    }

    public record UpdateCommand(String description, String unit, BigDecimal minimumStock, BigDecimal salePrice) {
    }

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final BatchRepository batches;
    private final MinimumStockService minimumStock;

    /** Creates a product. If the SKU belongs to a removed product, it is reactivated with the new data. */
    @Transactional
    public ProductView create(CreateCommand command) {
        String sku = command.sku().trim();
        Product existing = products.findBySku(sku).orElse(null);
        if (existing != null && existing.isActive()) {
            throw new DuplicateOperationException("Product already exists: " + sku);
        }

        String categoryName = command.category().trim();
        Category category = categories.findByName(categoryName)
                .orElseGet(() -> categories.save(new Category(categoryName, null)));

        Product product = existing != null ? existing : new Product();
        product.setSku(sku);
        product.setCategory(category);
        product.setControlsExpiry(command.controlsExpiry());
        product.setActive(true);
        if (command.sapItemCode() != null && !command.sapItemCode().isBlank()) {
            product.setSapItemCode(command.sapItemCode().trim());
        }
        applyDetails(product, command.description(), command.unit(), command.minimumStock(), command.salePrice());
        products.saveAndFlush(product);
        return toView(product, batches.availableBalance(product.getId()));
    }

    @Transactional
    public ProductView update(String sku, UpdateCommand command) {
        Product product = findActive(sku);
        applyDetails(product, command.description(), command.unit(), command.minimumStock(), command.salePrice());
        products.saveAndFlush(product);

        minimumStock.evaluate(product);
        return toView(product, batches.availableBalance(product.getId()));
    }

    /** Soft delete: the product disappears from the lists but its history is kept. */
    @Transactional
    public void deactivate(String sku) {
        Product product = findActive(sku);
        BigDecimal balance = batches.availableBalance(product.getId());
        if (balance.signum() > 0) {
            throw new BusinessRuleException("Cannot remove " + sku + ": it still has "
                    + balance.stripTrailingZeros().toPlainString() + " in stock");
        }
        product.setActive(false);
        products.saveAndFlush(product);
    }

    private Product findActive(String sku) {
        return products.findBySku(sku)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + sku));
    }

    private void applyDetails(Product product, String description, String unit, BigDecimal minimum, BigDecimal price) {
        product.setDescription(description.trim());
        product.setUnit(unit.trim());
        product.setMinimumStock(minimum);
        product.setSalePrice(price);
    }

    private ProductView toView(Product product, BigDecimal balance) {
        BigDecimal minimum = product.getMinimumStock();
        return new ProductView(product.getSku(), product.getDescription(), product.getUnit(),
                product.getAbcClass().name(), balance, minimum, product.getSalePrice(),
                minimum.signum() > 0 && balance.compareTo(minimum) <= 0);
    }
}
