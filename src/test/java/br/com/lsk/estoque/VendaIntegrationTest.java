package br.com.lsk.estoque;

import br.com.lsk.estoque.application.dto.EntradaNfeCommand;
import br.com.lsk.estoque.application.dto.EntradaNfeCommand.ItemEntrada;
import br.com.lsk.estoque.application.dto.VendaEvent;
import br.com.lsk.estoque.application.dto.VendaEvent.ItemVenda;
import br.com.lsk.estoque.application.service.EntradaEstoqueService;
import br.com.lsk.estoque.application.service.ProcessarVendaService;
import br.com.lsk.estoque.application.service.ProcessarVendaService.Resultado;
import br.com.lsk.estoque.domain.exception.EstoqueInsuficienteException;
import br.com.lsk.estoque.domain.exception.RecursoNaoEncontradoException;
import br.com.lsk.estoque.domain.model.Categoria;
import br.com.lsk.estoque.domain.model.Lote;
import br.com.lsk.estoque.domain.model.Produto;
import br.com.lsk.estoque.domain.model.TipoAlerta;
import br.com.lsk.estoque.domain.repository.AlertaEstoqueRepository;
import br.com.lsk.estoque.domain.repository.CategoriaRepository;
import br.com.lsk.estoque.domain.repository.LoteRepository;
import br.com.lsk.estoque.domain.repository.ProdutoRepository;
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
class VendaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired CategoriaRepository categorias;
    @Autowired ProdutoRepository produtos;
    @Autowired LoteRepository lotes;
    @Autowired AlertaEstoqueRepository alertas;
    @Autowired ProcessarVendaService vendas;
    @Autowired EntradaEstoqueService entrada;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void sameEventIdNeverDeductsTwice() {
        Produto p = produto("VND-1", "0");
        lote(p, "L1", 10);
        VendaEvent evento = venda("VND-1", "3");

        assertThat(vendas.processar(evento)).isEqualTo(Resultado.PROCESSADO);
        assertThat(vendas.processar(evento)).isEqualTo(Resultado.DUPLICADO);

        assertThat(saldo(p)).isEqualByComparingTo("7");
        assertThat(movimentacoes("VENDA-" + evento.eventId())).isEqualTo(1);
    }

    @Test
    void insufficientStockIsRejected() {
        Produto p = produto("VND-2", "0");
        lote(p, "L1", 2);

        assertThatThrownBy(() -> vendas.processar(venda("VND-2", "5")))
                .isInstanceOf(EstoqueInsuficienteException.class);
    }

    @Test
    void unknownSkuIsRejected() {
        assertThatThrownBy(() -> vendas.processar(venda("NAO-EXISTE", "1")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void alertOpensAtMinimumStaysSingleAndClosesOnReceipt() {
        Produto p = produto("VND-3", "5");
        lote(p, "L1", 10);

        vendas.processar(venda("VND-3", "5")); // balance 5 (= minimum)
        assertThat(alertaAberto(p)).isTrue();
        assertThat(totalAlertas(p)).isEqualTo(1);

        vendas.processar(venda("VND-3", "1")); // balance 4: still a single alert
        assertThat(totalAlertas(p)).isEqualTo(1);

        entrada.registrarNfe(new EntradaNfeCommand("CHAVE-VND-3", "1", List.of(
                new ItemEntrada("VND-3", "L2", new BigDecimal("10"), new BigDecimal("5"), null,
                        LocalDate.now().plusDays(60)))));
        assertThat(alertaAberto(p)).isFalse();
    }

    @Test
    void noAlertWhileAboveMinimum() {
        Produto p = produto("VND-4", "5");
        lote(p, "L1", 10);

        vendas.processar(venda("VND-4", "2")); // balance 8

        assertThat(totalAlertas(p)).isZero();
    }

    private static VendaEvent venda(String sku, String qtd) {
        return new VendaEvent(UUID.randomUUID(), "PED-" + UUID.randomUUID(), OffsetDateTime.now(),
                List.of(new ItemVenda(sku, new BigDecimal(qtd), new BigDecimal("10"))));
    }

    private Produto produto(String sku, String minimo) {
        Categoria c = categorias.save(new Categoria("Cat-" + sku, null));
        Produto p = new Produto();
        p.setSku(sku);
        p.setDescricao("Produto " + sku);
        p.setCategoria(c);
        p.setControlaValidade(false);
        p.setCustoMedio(new BigDecimal("5"));
        p.setPrecoVenda(new BigDecimal("10"));
        p.setEstoqueMinimo(new BigDecimal(minimo));
        return produtos.saveAndFlush(p);
    }

    private void lote(Produto p, String codigo, int qtd) {
        lotes.saveAndFlush(Lote.builder()
                .produto(p).codigoLote(codigo).dataEntrada(OffsetDateTime.now().minusDays(1))
                .quantidadeInicial(new BigDecimal(qtd)).quantidadeAtual(new BigDecimal(qtd))
                .custoUnitario(new BigDecimal("5")).build());
    }

    private BigDecimal saldo(Produto p) {
        em.flush();
        return lotes.saldoDisponivel(p.getId());
    }

    private boolean alertaAberto(Produto p) {
        em.flush();
        return alertas.existsByProdutoIdAndTipoAndResolvidoFalse(p.getId(), TipoAlerta.ESTOQUE_MINIMO);
    }

    private long totalAlertas(Produto p) {
        em.flush();
        return alertas.findAll().stream().filter(a -> a.getProduto().getId().equals(p.getId())).count();
    }

    private Integer movimentacoes(String referencia) {
        em.flush();
        return jdbc.queryForObject("SELECT count(*) FROM movimentacoes WHERE referencia = ?", Integer.class, referencia);
    }
}
