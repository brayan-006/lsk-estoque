package br.com.lsk.estoque;

import br.com.lsk.estoque.application.service.BaixaEstoqueService;
import br.com.lsk.estoque.application.service.CurvaAbcService;
import br.com.lsk.estoque.domain.model.Categoria;
import br.com.lsk.estoque.domain.model.ClasseAbc;
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
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** ABC by revenue: A up to 80 %, B up to 95 %, C the rest and items without sales (requires Docker). */
@SpringBootTest(properties = {
        "lsk.jobs.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
@Transactional
class CurvaAbcIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired CategoriaRepository categorias;
    @Autowired ProdutoRepository produtos;
    @Autowired LoteRepository lotes;
    @Autowired BaixaEstoqueService baixa;
    @Autowired CurvaAbcService curvaAbc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void classificaPorFaturamentoERebaixaQuemNaoVendeu() {
        // revenue: P1 = 1000, P2 = 300, P3 = 100, P4 = 10 (total 1410)
        Produto p1 = vendido("ABC-1", 10, "100");
        Produto p2 = vendido("ABC-2", 3, "100");
        Produto p3 = vendido("ABC-3", 1, "100");
        Produto p4 = vendido("ABC-4", 1, "10");
        Produto p5 = produto("ABC-5");
        p5.setClasseAbc(ClasseAbc.A); // old class, no sales in the window
        produtos.saveAndFlush(p5);

        curvaAbc.recalcular();

        assertThat(classe(p1)).isEqualTo("A"); // 0 % accumulated before it
        assertThat(classe(p2)).isEqualTo("A"); // ~70.9 % before it (< 80 %)
        assertThat(classe(p3)).isEqualTo("B"); // ~92.2 % before it (< 95 %)
        assertThat(classe(p4)).isEqualTo("C"); // ~99.3 % before it
        assertThat(classe(p5)).isEqualTo("C"); // no sales: downgraded
    }

    private Produto vendido(String sku, int qtd, String preco) {
        Produto p = produto(sku);
        lotes.saveAndFlush(Lote.builder()
                .produto(p).codigoLote("L1").dataEntrada(OffsetDateTime.now().minusDays(1))
                .quantidadeInicial(new BigDecimal("100")).quantidadeAtual(new BigDecimal("100"))
                .custoUnitario(new BigDecimal("5")).build());
        baixa.darBaixa(p, new BigDecimal(qtd), "VENDA-" + sku, "VENDA", new BigDecimal(preco), "PED-" + sku);
        return p;
    }

    private Produto produto(String sku) {
        Categoria c = categorias.save(new Categoria("Cat-" + sku, null));
        Produto p = new Produto();
        p.setSku(sku);
        p.setDescricao("Produto " + sku);
        p.setCategoria(c);
        p.setControlaValidade(false);
        p.setCustoMedio(new BigDecimal("5"));
        p.setPrecoVenda(new BigDecimal("10"));
        return produtos.saveAndFlush(p);
    }

    private String classe(Produto p) {
        em.flush();
        return jdbc.queryForObject("SELECT classe_abc FROM produtos WHERE id = ?", String.class, p.getId()).trim();
    }
}
