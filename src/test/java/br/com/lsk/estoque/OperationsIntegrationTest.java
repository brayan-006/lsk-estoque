package br.com.lsk.estoque;

import br.com.lsk.estoque.application.service.ProductService;
import br.com.lsk.estoque.application.service.ProductService.CreateCommand;
import br.com.lsk.estoque.application.service.ProductService.UpdateCommand;
import br.com.lsk.estoque.domain.exception.OperacaoDuplicadaException;
import br.com.lsk.estoque.domain.exception.RecursoNaoEncontradoException;
import br.com.lsk.estoque.domain.exception.RegraDeNegocioException;
import br.com.lsk.estoque.domain.model.Lote;
import br.com.lsk.estoque.domain.model.Produto;
import br.com.lsk.estoque.domain.model.TipoAlerta;
import br.com.lsk.estoque.domain.repository.AlertaEstoqueRepository;
import br.com.lsk.estoque.domain.repository.CategoriaRepository;
import br.com.lsk.estoque.domain.repository.LoteRepository;
import br.com.lsk.estoque.domain.repository.ProdutoRepository;
import br.com.lsk.estoque.web.dto.DashboardViews.ProductView;
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

    @Autowired ProductService products;
    @Autowired ProdutoRepository produtos;
    @Autowired CategoriaRepository categorias;
    @Autowired LoteRepository lotes;
    @Autowired AlertaEstoqueRepository alertas;
    @Autowired EntityManager em;

    @Test
    void createsProductAndCategory() {
        ProductView view = products.create(command("OPS-1", "0"));

        assertThat(view.abcClass()).isEqualTo("C");
        assertThat(view.balance()).isEqualByComparingTo("0");
        assertThat(produtos.findBySku("OPS-1")).isPresent();
        assertThat(categorias.findByNome("Cat-OPS")).isPresent();
    }

    @Test
    void rejectsDuplicatedSku() {
        products.create(command("OPS-2", "0"));

        assertThatThrownBy(() -> products.create(command("OPS-2", "0")))
                .isInstanceOf(OperacaoDuplicadaException.class);
    }

    @Test
    void updatingMinimumStockOpensAndClosesTheAlert() {
        products.create(command("OPS-3", "0"));
        Produto product = produtos.findBySku("OPS-3").orElseThrow();
        lotes.saveAndFlush(Lote.builder()
                .produto(product).codigoLote("L1").dataEntrada(OffsetDateTime.now().minusDays(1))
                .quantidadeInicial(new BigDecimal("10")).quantidadeAtual(new BigDecimal("10"))
                .custoUnitario(new BigDecimal("5")).build());

        ProductView raised = products.update("OPS-3", update("10"));
        assertThat(raised.belowMinimum()).isTrue();
        assertThat(alertas.existsByProdutoIdAndTipoAndResolvidoFalse(product.getId(), TipoAlerta.ESTOQUE_MINIMO)).isTrue();

        ProductView lowered = products.update("OPS-3", update("2"));
        assertThat(lowered.belowMinimum()).isFalse();
        em.flush();
        assertThat(alertas.existsByProdutoIdAndTipoAndResolvidoFalse(product.getId(), TipoAlerta.ESTOQUE_MINIMO)).isFalse();
    }

    @Test
    void updateFailsForUnknownSku() {
        assertThatThrownBy(() -> products.update("DOES-NOT-EXIST", update("1")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deactivatesProductWithoutStock() {
        products.create(command("OPS-4", "0"));

        products.deactivate("OPS-4");

        assertThat(produtos.findBySku("OPS-4").orElseThrow().isAtivo()).isFalse();
        assertThatThrownBy(() -> products.update("OPS-4", update("1")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void refusesToRemoveProductWithStock() {
        products.create(command("OPS-5", "0"));
        Produto product = produtos.findBySku("OPS-5").orElseThrow();
        lotes.saveAndFlush(Lote.builder()
                .produto(product).codigoLote("L1").dataEntrada(OffsetDateTime.now().minusDays(1))
                .quantidadeInicial(new BigDecimal("3")).quantidadeAtual(new BigDecimal("3"))
                .custoUnitario(new BigDecimal("5")).build());

        assertThatThrownBy(() -> products.deactivate("OPS-5")).isInstanceOf(RegraDeNegocioException.class);
        assertThat(produtos.findBySku("OPS-5").orElseThrow().isAtivo()).isTrue();
    }

    @Test
    void creatingARemovedSkuReactivatesIt() {
        products.create(command("OPS-6", "0"));
        products.deactivate("OPS-6");

        ProductView view = products.create(new CreateCommand("OPS-6", "New description", "Cat-OPS", "UN",
                new BigDecimal("1"), new BigDecimal("20"), false, null));

        assertThat(view.description()).isEqualTo("New description");
        assertThat(produtos.findBySku("OPS-6").orElseThrow().isAtivo()).isTrue();
    }

    private static CreateCommand command(String sku, String minimum) {
        return new CreateCommand(sku, "Test product " + sku, "Cat-OPS", "UN", new BigDecimal(minimum),
                new BigDecimal("10"), false, null);
    }

    private static UpdateCommand update(String minimum) {
        return new UpdateCommand("Updated description", "UN", new BigDecimal(minimum), new BigDecimal("12"));
    }
}