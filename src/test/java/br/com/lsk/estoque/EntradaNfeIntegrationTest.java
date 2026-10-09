package br.com.lsk.estoque;

import br.com.lsk.estoque.application.dto.EntradaNfeCommand;
import br.com.lsk.estoque.application.dto.EntradaNfeCommand.ItemEntrada;
import br.com.lsk.estoque.application.dto.ResultadoEntrada;
import br.com.lsk.estoque.application.service.EntradaEstoqueService;
import br.com.lsk.estoque.domain.exception.OperacaoDuplicadaException;
import br.com.lsk.estoque.domain.exception.RecursoNaoEncontradoException;
import br.com.lsk.estoque.domain.exception.RegraDeNegocioException;
import br.com.lsk.estoque.domain.model.Categoria;
import br.com.lsk.estoque.domain.model.Lote;
import br.com.lsk.estoque.domain.model.Produto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Entrada de NF-e: lote, movimentação, custo médio, idempotência e validações (requer Docker). */
@SpringBootTest(properties = {
        "lsk.jobs.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
@Transactional
class EntradaNfeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired CategoriaRepository categorias;
    @Autowired ProdutoRepository produtos;
    @Autowired LoteRepository lotes;
    @Autowired EntradaEstoqueService entrada;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void criaLoteEMovimentacaoDeEntrada() {
        Produto p = produto("ENT-1", true);

        ResultadoEntrada r = entrada.registrarNfe(nfe("CHAVE-1", item("ENT-1", "L1", "10", "8", hoje(30))));

        assertThat(r.referencia()).isEqualTo("NFE-CHAVE-1");
        assertThat(r.itensProcessados()).isEqualTo(1);
        assertThat(r.lotes().get(0).saldoDoSku()).isEqualByComparingTo("10");
        assertThat(saldoDoLote(p, "L1")).isEqualByComparingTo("10");
        assertThat(movimentacoes("NFE-CHAVE-1")).isEqualTo(1);
        assertThat(p.getCustoMedio()).isEqualByComparingTo("8");
    }

    @Test
    void mesmaChaveDeAcessoNaoEntraDuasVezes() {
        produto("ENT-2", true);
        EntradaNfeCommand cmd = nfe("CHAVE-2", item("ENT-2", "L1", "10", "8", hoje(30)));

        entrada.registrarNfe(cmd);

        assertThatThrownBy(() -> entrada.registrarNfe(cmd)).isInstanceOf(OperacaoDuplicadaException.class);
        assertThat(movimentacoes("NFE-CHAVE-2")).isEqualTo(1);
    }

    @Test
    void calculaCustoMedioPonderado() {
        Produto p = produto("ENT-3", true);
        lote(p, "VELHO", hoje(60), "10");

        entrada.registrarNfe(nfe("CHAVE-3", item("ENT-3", "NOVO", "10", "7", hoje(90))));

        // (10 x 5 + 10 x 7) / 20 = 6
        assertThat(p.getCustoMedio()).isEqualByComparingTo("6");
    }

    @Test
    void mesmoCodigoDeLoteSomaAQuantidade() {
        Produto p = produto("ENT-4", true);
        LocalDate validade = hoje(30);
        lote(p, "A", validade, "10");

        entrada.registrarNfe(nfe("CHAVE-4", item("ENT-4", "A", "5", "5", validade)));

        assertThat(saldoDoLote(p, "A")).isEqualByComparingTo("15");
    }

    @Test
    void recusaLoteJaVencido() {
        produto("ENT-5", true);

        assertThatThrownBy(() -> entrada.registrarNfe(nfe("CHAVE-5", item("ENT-5", "L1", "10", "8", hoje(-1)))))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void exigeValidadeQuandoOProdutoControla() {
        produto("ENT-6", true);

        assertThatThrownBy(() -> entrada.registrarNfe(nfe("CHAVE-6", item("ENT-6", "L1", "10", "8", null))))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void recusaQuantidadeZeroOuNegativa() {
        produto("ENT-7", false);

        assertThatThrownBy(() -> entrada.registrarNfe(nfe("CHAVE-7", item("ENT-7", "L1", "0", "8", null))))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void recusaSkuInexistente() {
        assertThatThrownBy(() -> entrada.registrarNfe(nfe("CHAVE-8", item("NAO-EXISTE", "L1", "1", "8", null))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ------------------------------------------------------------------ helpers

    private static EntradaNfeCommand nfe(String chave, ItemEntrada... itens) {
        return new EntradaNfeCommand(chave, "123", List.of(itens));
    }

    private static ItemEntrada item(String sku, String lote, String qtd, String custo, LocalDate validade) {
        return new ItemEntrada(sku, lote, new BigDecimal(qtd), new BigDecimal(custo), null, validade);
    }

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

    private void lote(Produto p, String codigo, LocalDate validade, String qtd) {
        lotes.saveAndFlush(Lote.builder()
                .produto(p).codigoLote(codigo).dataValidade(validade).dataEntrada(OffsetDateTime.now().minusDays(1))
                .quantidadeInicial(new BigDecimal(qtd)).quantidadeAtual(new BigDecimal(qtd))
                .custoUnitario(new BigDecimal("5")).build());
    }

    private static LocalDate hoje(int plusDias) {
        return LocalDate.now().plusDays(plusDias);
    }

    private BigDecimal saldoDoLote(Produto p, String codigo) {
        em.flush();
        return jdbc.queryForObject("SELECT quantidade_atual FROM lotes WHERE produto_id = ? AND codigo_lote = ?",
                BigDecimal.class, p.getId(), codigo);
    }

    private Integer movimentacoes(String referencia) {
        em.flush();
        return jdbc.queryForObject("SELECT count(*) FROM movimentacoes WHERE referencia = ?", Integer.class, referencia);
    }
}
