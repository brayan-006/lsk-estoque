package br.com.lsk.estoque.domain.repository;

import br.com.lsk.estoque.domain.model.Lote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoteRepository extends JpaRepository<Lote, Long> {

    interface SaldoProduto {
        Long getProdutoId();

        BigDecimal getSaldo();
    }

    /**
     * Lotes disponíveis na ORDEM EM QUE SERÃO CONSUMIDOS (espelha a pr_dar_baixa_fefo):
     * 1) com validade, do que vence primeiro ao último; 2) sem validade por ordem de entrada (PEPS).
     * Ignora lotes vencidos, bloqueados e esgotados.
     */
    @Query("""
            select l from Lote l
             where l.produto.id = :produtoId
               and l.status = br.com.lsk.estoque.domain.model.StatusLote.ATIVO
               and l.quantidadeAtual > 0
               and (l.dataValidade is null or l.dataValidade >= current_date)
             order by case when l.dataValidade is null then 1 else 0 end,
                      l.dataValidade asc, l.dataEntrada asc, l.id asc
            """)
    List<Lote> findDisponiveisEmOrdemFefo(@Param("produtoId") Long produtoId);

    /** Ordenação PEPS pura (por entrada), útil para produtos que não controlam validade. */
    @Query("""
            select l from Lote l
             where l.produto.id = :produtoId
               and l.status = br.com.lsk.estoque.domain.model.StatusLote.ATIVO
               and l.quantidadeAtual > 0
             order by l.dataEntrada asc, l.id asc
            """)
    List<Lote> findDisponiveisEmOrdemPeps(@Param("produtoId") Long produtoId);

    /** Lotes ativos que vencem até a data informada (alertas de vencimento próximo). */
    @Query("""
            select l from Lote l join fetch l.produto
             where l.status = br.com.lsk.estoque.domain.model.StatusLote.ATIVO
               and l.quantidadeAtual > 0
               and l.dataValidade is not null
               and l.dataValidade between current_date and :limite
             order by l.dataValidade asc, l.id asc
            """)
    List<Lote> findVencendoAte(@Param("limite") LocalDate limite);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lote l where l.produto.id = :produtoId and l.codigoLote = :codigo")
    Optional<Lote> buscarParaAtualizar(@Param("produtoId") Long produtoId, @Param("codigo") String codigo);

    @Query("""
            select sum(l.quantidadeAtual) from Lote l
             where l.produto.id = :produtoId
               and l.status = br.com.lsk.estoque.domain.model.StatusLote.ATIVO
               and (l.dataValidade is null or l.dataValidade >= current_date)
            """)
    BigDecimal somarDisponivel(@Param("produtoId") Long produtoId);

    default BigDecimal saldoDisponivel(Long produtoId) {
        BigDecimal soma = somarDisponivel(produtoId);
        return soma == null ? BigDecimal.ZERO : soma;
    }

    @Query("""
            select l.produto.id as produtoId, sum(l.quantidadeAtual) as saldo
              from Lote l
             where l.produto.id in :ids
               and l.status = br.com.lsk.estoque.domain.model.StatusLote.ATIVO
               and (l.dataValidade is null or l.dataValidade >= current_date)
             group by l.produto.id
            """)
    List<SaldoProduto> saldosPorProduto(@Param("ids") Collection<Long> ids);

    @Transactional
    @Modifying
    @Query("""
            update Lote l set l.status = br.com.lsk.estoque.domain.model.StatusLote.VENCIDO
             where l.status = br.com.lsk.estoque.domain.model.StatusLote.ATIVO
               and l.dataValidade < :hoje
            """)
    int marcarVencidos(@Param("hoje") LocalDate hoje);
}
