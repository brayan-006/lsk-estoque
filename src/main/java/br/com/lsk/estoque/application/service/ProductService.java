package br.com.lsk.estoque.application.service;

import br.com.lsk.estoque.domain.exception.OperacaoDuplicadaException;
import br.com.lsk.estoque.domain.exception.RecursoNaoEncontradoException;
import br.com.lsk.estoque.domain.exception.RegraDeNegocioException;
import br.com.lsk.estoque.domain.model.Categoria;
import br.com.lsk.estoque.domain.model.Produto;
import br.com.lsk.estoque.domain.repository.CategoriaRepository;
import br.com.lsk.estoque.domain.repository.LoteRepository;
import br.com.lsk.estoque.domain.repository.ProdutoRepository;
import br.com.lsk.estoque.web.dto.DashboardViews.ProductView;
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

    private final ProdutoRepository products;
    private final CategoriaRepository categories;
    private final LoteRepository batches;
    private final EstoqueMinimoService minimumStock;

    /** Creates a product. If the SKU belongs to a removed product, it is reactivated with the new data. */
    @Transactional
    public ProductView create(CreateCommand command) {
        String sku = command.sku().trim();
        Produto existing = products.findBySku(sku).orElse(null);
        if (existing != null && existing.isAtivo()) {
            throw new OperacaoDuplicadaException("Product already exists: " + sku);
        }

        String categoryName = command.category().trim();
        Categoria category = categories.findByNome(categoryName)
                .orElseGet(() -> categories.save(new Categoria(categoryName, null)));

        Produto product = existing != null ? existing : new Produto();
        product.setSku(sku);
        product.setCategoria(category);
        product.setControlaValidade(command.controlsExpiry());
        product.setAtivo(true);
        if (command.sapItemCode() != null && !command.sapItemCode().isBlank()) {
            product.setSapItemCode(command.sapItemCode().trim());
        }
        applyDetails(product, command.description(), command.unit(), command.minimumStock(), command.salePrice());
        products.saveAndFlush(product);
        return toView(product, batches.saldoDisponivel(product.getId()));
    }

    @Transactional
    public ProductView update(String sku, UpdateCommand command) {
        Produto product = findActive(sku);
        applyDetails(product, command.description(), command.unit(), command.minimumStock(), command.salePrice());
        products.saveAndFlush(product);

        minimumStock.avaliar(product);
        return toView(product, batches.saldoDisponivel(product.getId()));
    }

    /** Soft delete: the product disappears from the lists but its history is kept. */
    @Transactional
    public void deactivate(String sku) {
        Produto product = findActive(sku);
        BigDecimal balance = batches.saldoDisponivel(product.getId());
        if (balance.signum() > 0) {
            throw new RegraDeNegocioException("Cannot remove " + sku + ": it still has "
                    + balance.stripTrailingZeros().toPlainString() + " in stock");
        }
        product.setAtivo(false);
        products.saveAndFlush(product);
    }

    private Produto findActive(String sku) {
        return products.findBySku(sku)
                .filter(Produto::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Product not found: " + sku));
    }

    private void applyDetails(Produto product, String description, String unit, BigDecimal minimum, BigDecimal price) {
        product.setDescricao(description.trim());
        product.setUnidade(unit.trim());
        product.setEstoqueMinimo(minimum);
        product.setPrecoVenda(price);
    }

    private ProductView toView(Produto product, BigDecimal balance) {
        BigDecimal minimum = product.getEstoqueMinimo();
        return new ProductView(product.getSku(), product.getDescricao(), product.getUnidade(),
                product.getClasseAbc().name(), balance, minimum, product.getPrecoVenda(),
                minimum.signum() > 0 && balance.compareTo(minimum) <= 0);
    }
}