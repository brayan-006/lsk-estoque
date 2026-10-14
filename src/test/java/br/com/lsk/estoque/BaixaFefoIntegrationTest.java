package br.com.lsk.estoque;

import br.com.lsk.estoque.application.dto.LoteBaixado;
import br.com.lsk.estoque.application.service.BaixaEstoqueService;
import br.com.lsk.estoque.domain.exception.EstoqueInsuficienteException;
import br.com.lsk.estoque.domain.model.Categoria;
import br.com.lsk.estoque.domain.model.CriterioBaixa;
import br.com.lsk.estoque.domain.model.Lote;
import br.com.lsk.estoque.domain.model.Produto;
import br.com.lsk.estoque.domain.repository.CategoriaRepository;
import br.com.lsk.estoque.domain.repository.LoteRepository;
import br.com.lsk.estoque.domain.repository.ProdutoRepository;
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

/** Exercises pr_dar_baixa_fefo end to end against a real PostgreSQL (requires Docker). */
@SpringBootTest(properties = {
        "lsk.jobs.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
@Transactional
class BaixaFefoIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired CategoriaRepository categorias;
    @Autowired ProdutoRepository produtos;
    @Autowired LoteRepository lotes;
    @Autowired BaixaEstoqueService baixa;
    @Autowired JdbcTemplate jdbc;

    @Test
    void picksTheEarliestExpiryFirstRegardlessOfReceiptOrder() {
        Produto p = product("FEFO-1", true);
        batch(p, "A", expiresIn(30), receivedDaysAgo(3), "10");
        batch(p, "B", expiresIn(10), receivedDaysAgo(2), "5");

        List<LoteBaixado> r = baixa.darBaixa(p, new BigDecimal("12"), "REF-1", "VENDA", new BigDecimal("10"), "PED-1");

        assertThat(r).extracting(LoteBaixado::codigoLote).containsExactly("B", "A");
        assertThat(r.get(0).quantidade()).isEqualByComparingTo("5");
        assertThat(r.get(1).quantidade()).isEqualByComparingTo("7");
        assertThat(r).allMatch(l -> l.criterio() == CriterioBaixa.FEFO);
        assertThat(batchBalance(p, "B")).isEqualByComparingTo("0");
        assertThat(batchStatus(p, "B")).isEqualTo("ESGOTADO");
        assertThat(batchBalance(p, "A")).isEqualByComparingTo("3");
        assertThat(movementCount("REF-1")).isEqualTo(2);
    }

    @Test
    void fallsBackToFifoWhenBatchesHaveNoExpiry() {
        Produto p = product("PEPS-1", false);
        batch(p, "X", null, receivedDaysAgo(10), "4");
        batch(p, "Y", null, receivedDaysAgo(5), "4");

        List<LoteBaixado> r = baixa.darBaixa(p, new BigDecimal("6"), "REF-2", "VENDA", null, null);

        assertThat(r).extracting(LoteBaixado::codigoLote).containsExactly("X", "Y");
        assertThat(r).allMatch(l -> l.criterio() == CriterioBaixa.PEPS);
        assertThat(batchBalance(p, "Y")).isEqualByComparingTo("2");
    }

    @Test
    void batchesWithExpiryLeaveBeforeBatchesWithout() {
        Produto p = product("MIX-1", false);
        batch(p, "NO-EXPIRY", null, receivedDaysAgo(30), "10");
        batch(p, "WITH-EXPIRY", expiresIn(20), receivedDaysAgo(1), "10");

        List<LoteBaixado> r = baixa.darBaixa(p, new BigDecimal("12"), "REF-3", "VENDA", null, null);

        assertThat(r).extracting(LoteBaixado::codigoLote).containsExactly("WITH-EXPIRY", "NO-EXPIRY");
        assertThat(r.get(0).criterio()).isEqualTo(CriterioBaixa.FEFO);
        assertThat(r.get(1).criterio()).isEqualTo(CriterioBaixa.PEPS);
    }

    @Test
    void expiredBatchIsNeverPicked() {
        Produto p = product("VENC-1", true);
        batch(p, "EXPIRED", expiresIn(-1), receivedDaysAgo(60), "100");
        batch(p, "OK", expiresIn(5), receivedDaysAgo(1), "3");

        assertThatThrownBy(() -> baixa.darBaixa(p, new BigDecimal("5"), "REF-4", "VENDA", null, null))
                .isInstanceOf(EstoqueInsuficienteException.class);
    }

    private Produto product(String sku, boolean controlsExpiry) {
        Categoria c = categorias.save(new Categoria("Cat-" + sku, null));
        Produto p = new Produto();
        p.setSku(sku);
        p.setDescricao("Product " + sku);
        p.setCategoria(c);
        p.setControlaValidade(controlsExpiry);
        p.setCustoMedio(new BigDecimal("5"));
        p.setPrecoVenda(new BigDecimal("10"));
        return produtos.saveAndFlush(p);
    }

    private void batch(Produto p, String code, LocalDate expiry, OffsetDateTime received, String qty) {
        lotes.saveAndFlush(Lote.builder()
                .produto(p).codigoLote(code).dataValidade(expiry).dataEntrada(received)
                .quantidadeInicial(new BigDecimal(qty)).quantidadeAtual(new BigDecimal(qty))
                .custoUnitario(new BigDecimal("5")).build());
    }

    private static LocalDate expiresIn(int days) {
        return LocalDate.now().plusDays(days);
    }

    private static OffsetDateTime receivedDaysAgo(int days) {
        return OffsetDateTime.now().minusDays(days);
    }

    private BigDecimal batchBalance(Produto p, String code) {
        return jdbc.queryForObject("SELECT quantidade_atual FROM lotes WHERE produto_id = ? AND codigo_lote = ?",
                BigDecimal.class, p.getId(), code);
    }

    private String batchStatus(Produto p, String code) {
        return jdbc.queryForObject("SELECT status FROM lotes WHERE produto_id = ? AND codigo_lote = ?",
                String.class, p.getId(), code);
    }

    private Integer movementCount(String reference) {
        return jdbc.queryForObject("SELECT count(*) FROM movimentacoes WHERE referencia = ?", Integer.class, reference);
    }
}
