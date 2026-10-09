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

/** Valida a pr_dar_baixa_fefo de ponta a ponta contra um PostgreSQL real (requer Docker). */
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
    void consomeOLoteQueVenceAntesIndependenteDaOrdemDeEntrada() {
        Produto p = produto("FEFO-1", true);
        lote(p, "A", hoje(30), dias(-3), "10");
        lote(p, "B", hoje(10), dias(-2), "5");

        List<LoteBaixado> r = baixa.darBaixa(p, new BigDecimal("12"), "REF-1", "VENDA", new BigDecimal("10"), "PED-1");

        assertThat(r).extracting(LoteBaixado::codigoLote).containsExactly("B", "A");
        assertThat(r.get(0).quantidade()).isEqualByComparingTo("5");
        assertThat(r.get(1).quantidade()).isEqualByComparingTo("7");
        assertThat(r).allMatch(l -> l.criterio() == CriterioBaixa.FEFO);
        assertThat(saldoDoLote(p, "B")).isEqualByComparingTo("0");
        assertThat(statusDoLote(p, "B")).isEqualTo("ESGOTADO");
        assertThat(saldoDoLote(p, "A")).isEqualByComparingTo("3");
        assertThat(movimentacoes("REF-1")).isEqualTo(2);
    }

    @Test
    void usaPepsQuandoOsLotesNaoTemValidade() {
        Produto p = produto("PEPS-1", false);
        lote(p, "X", null, dias(-10), "4");
        lote(p, "Y", null, dias(-5), "4");

        List<LoteBaixado> r = baixa.darBaixa(p, new BigDecimal("6"), "REF-2", "VENDA", null, null);

        assertThat(r).extracting(LoteBaixado::codigoLote).containsExactly("X", "Y");
        assertThat(r).allMatch(l -> l.criterio() == CriterioBaixa.PEPS);
        assertThat(saldoDoLote(p, "Y")).isEqualByComparingTo("2");
    }

    @Test
    void lotesComValidadeSaemAntesDosSemValidade() {
        Produto p = produto("MIX-1", false);
        lote(p, "SEM-VAL", null, dias(-30), "10");
        lote(p, "COM-VAL", hoje(20), dias(-1), "10");

        List<LoteBaixado> r = baixa.darBaixa(p, new BigDecimal("12"), "REF-3", "VENDA", null, null);

        assertThat(r).extracting(LoteBaixado::codigoLote).containsExactly("COM-VAL", "SEM-VAL");
        assertThat(r.get(0).criterio()).isEqualTo(CriterioBaixa.FEFO);
        assertThat(r.get(1).criterio()).isEqualTo(CriterioBaixa.PEPS);
    }

    @Test
    void loteVencidoNuncaEConsumido() {
        Produto p = produto("VENC-1", true);
        lote(p, "VENCIDO", hoje(-1), dias(-60), "100");
        lote(p, "OK", hoje(5), dias(-1), "3");

        assertThatThrownBy(() -> baixa.darBaixa(p, new BigDecimal("5"), "REF-4", "VENDA", null, null))
                .isInstanceOf(EstoqueInsuficienteException.class);
    }

    // ------------------------------------------------------------------ helpers

    private Produto produto(String sku, boolean controlaValidade) {
        Categoria c = categorias.save(new Categoria("Cat-" + sku, null));
        Produto p = new Produto();
        p.setSku(sku);
        p.setDescricao("Produto " + sku);
        p.setCategoria(c);
        p.setControlaValidade(controlaValidade);
        p.setCustoMedio(new BigDecimal("5"));
        p.setPrecoVenda(new BigDecimal("10"));
        return produtos.saveAndFlush(p);
    }

    private void lote(Produto p, String codigo, LocalDate validade, OffsetDateTime entrada, String qtd) {
        lotes.saveAndFlush(Lote.builder()
                .produto(p).codigoLote(codigo).dataValidade(validade).dataEntrada(entrada)
                .quantidadeInicial(new BigDecimal(qtd)).quantidadeAtual(new BigDecimal(qtd))
                .custoUnitario(new BigDecimal("5")).build());
    }

    private static LocalDate hoje(int plusDias) {
        return LocalDate.now().plusDays(plusDias);
    }

    private static OffsetDateTime dias(int plusDias) {
        return OffsetDateTime.now().plusDays(plusDias);
    }

    private BigDecimal saldoDoLote(Produto p, String codigo) {
        return jdbc.queryForObject("SELECT quantidade_atual FROM lotes WHERE produto_id = ? AND codigo_lote = ?",
                BigDecimal.class, p.getId(), codigo);
    }

    private String statusDoLote(Produto p, String codigo) {
        return jdbc.queryForObject("SELECT status FROM lotes WHERE produto_id = ? AND codigo_lote = ?",
                String.class, p.getId(), codigo);
    }

    private Integer movimentacoes(String referencia) {
        return jdbc.queryForObject("SELECT count(*) FROM movimentacoes WHERE referencia = ?", Integer.class, referencia);
    }
}
