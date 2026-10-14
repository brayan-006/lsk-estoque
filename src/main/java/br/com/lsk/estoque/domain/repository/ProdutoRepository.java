package br.com.lsk.estoque.domain.repository;

import br.com.lsk.estoque.domain.model.ClasseAbc;
import br.com.lsk.estoque.domain.model.Produto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    Optional<Produto> findBySku(String sku);

    /** SELECT ... FOR UPDATE, to serialize operations on the same SKU. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p where p.sku = :sku")
    Optional<Produto> buscarParaAtualizar(@Param("sku") String sku);

    List<Produto> findByClasseAbcAndAtivoTrueOrderBySkuAsc(ClasseAbc classeAbc);

    /** Active products whose sellable balance is at or below the minimum. */
    @Query(value = """
            SELECT p.*
              FROM produtos p
             WHERE p.ativo
               AND p.estoque_minimo > 0
               AND p.estoque_minimo >= COALESCE((
                     SELECT SUM(l.quantidade_atual)
                       FROM lotes l
                      WHERE l.produto_id = p.id
                        AND l.status = 'ATIVO'
                        AND (l.data_validade IS NULL OR l.data_validade >= CURRENT_DATE)), 0)
            """, nativeQuery = true)
    List<Produto> findAbaixoDoMinimo();

    /**
     * ABC by revenue (price x quantity of VENDA outflows in the window), with turnover as the tiebreaker.
     * A product is class A while the cumulative share before it is still under the A limit.
     */
    @Modifying
    @Query(value = """
            WITH vendas AS (
                SELECT m.produto_id,
                       SUM(m.quantidade * m.preco_unitario) AS faturamento,
                       SUM(m.quantidade)                    AS giro
                  FROM movimentacoes m
                 WHERE m.tipo = 'SAIDA'
                   AND m.origem = 'VENDA'
                   AND m.preco_unitario IS NOT NULL
                   AND m.criado_em >= :desde
                 GROUP BY m.produto_id
                HAVING SUM(m.quantidade * m.preco_unitario) > 0
            ), ranking AS (
                SELECT v.produto_id,
                       (SUM(v.faturamento) OVER (ORDER BY v.faturamento DESC, v.giro DESC, v.produto_id
                                                 ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
                        - v.faturamento) / SUM(v.faturamento) OVER () AS participacao_anterior
                  FROM vendas v
            )
            UPDATE produtos p
               SET classe_abc = CASE WHEN r.participacao_anterior < :limiteA THEN 'A'
                                     WHEN r.participacao_anterior < :limiteB THEN 'B'
                                     ELSE 'C' END,
                   atualizado_em = now()
              FROM ranking r
             WHERE p.id = r.produto_id
               AND p.classe_abc IS DISTINCT FROM CASE WHEN r.participacao_anterior < :limiteA THEN 'A'
                                                      WHEN r.participacao_anterior < :limiteB THEN 'B'
                                                      ELSE 'C' END
            """, nativeQuery = true)
    int atualizarClassesComVendas(@Param("desde") OffsetDateTime desde,
                                  @Param("limiteA") BigDecimal limiteA,
                                  @Param("limiteB") BigDecimal limiteB);

    /** Products without revenue in the window drop to class C. */
    @Modifying
    @Query(value = """
            UPDATE produtos p
               SET classe_abc = 'C', atualizado_em = now()
             WHERE p.classe_abc <> 'C'
               AND NOT EXISTS (
                     SELECT 1
                       FROM movimentacoes m
                      WHERE m.produto_id = p.id
                        AND m.tipo = 'SAIDA'
                        AND m.origem = 'VENDA'
                        AND m.preco_unitario IS NOT NULL
                        AND m.quantidade * m.preco_unitario > 0
                        AND m.criado_em >= :desde)
            """, nativeQuery = true)
    int rebaixarSemVendas(@Param("desde") OffsetDateTime desde);
}
