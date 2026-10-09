package br.com.lsk.estoque.domain.repository;

import br.com.lsk.estoque.domain.model.AlertaEstoque;
import br.com.lsk.estoque.domain.model.TipoAlerta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

public interface AlertaEstoqueRepository extends JpaRepository<AlertaEstoque, Long> {

    boolean existsByProdutoIdAndTipoAndResolvidoFalse(Long produtoId, TipoAlerta tipo);

    @Transactional
    @Modifying
    @Query("""
            update AlertaEstoque a set a.resolvido = true, a.resolvidoEm = :agora
             where a.produto.id = :produtoId and a.tipo = :tipo and a.resolvido = false
            """)
    int resolverAbertos(@Param("produtoId") Long produtoId,
                        @Param("tipo") TipoAlerta tipo,
                        @Param("agora") OffsetDateTime agora);
}
