package br.com.lsk.estoque.domain.repository;

import br.com.lsk.estoque.domain.model.Movimentacao;
import br.com.lsk.estoque.domain.model.StatusSap;
import br.com.lsk.estoque.domain.model.TipoMovimentacao;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    interface ReferenciaPendente {
        String getReferencia();

        TipoMovimentacao getTipo();
    }

    @Query("""
            select m from Movimentacao m
              join fetch m.produto
              join fetch m.lote
             where m.referencia = :referencia and m.tipo = :tipo and m.sapStatus = :status
             order by m.id
            """)
    List<Movimentacao> buscarParaEnvio(@Param("referencia") String referencia,
                                       @Param("tipo") TipoMovimentacao tipo,
                                       @Param("status") StatusSap status);

    /** Atomically claims the movements of a reference (PENDENTE/ERRO to ENVIANDO); only one caller gets count > 0. */
    @Transactional
    @Modifying
    @Query("""
            update Movimentacao m
               set m.sapStatus = br.com.lsk.estoque.domain.model.StatusSap.ENVIANDO,
                   m.sapTentativas = m.sapTentativas + 1,
                   m.sapAtualizadoEm = :agora
             where m.referencia = :referencia
               and m.tipo = :tipo
               and m.sapStatus in (:elegiveis)
               and m.sapTentativas < :maxTentativas
            """)
    int reivindicar(@Param("referencia") String referencia,
                    @Param("tipo") TipoMovimentacao tipo,
                    @Param("elegiveis") Collection<StatusSap> elegiveis,
                    @Param("maxTentativas") int maxTentativas,
                    @Param("agora") OffsetDateTime agora);

    @Transactional
    @Modifying
    @Query("""
            update Movimentacao m
               set m.sapStatus = :novoStatus,
                   m.sapDocEntry = :docEntry,
                   m.sapErro = :erro,
                   m.sapAtualizadoEm = :agora
             where m.referencia = :referencia
               and m.tipo = :tipo
               and m.sapStatus = br.com.lsk.estoque.domain.model.StatusSap.ENVIANDO
            """)
    int finalizarEnvio(@Param("referencia") String referencia,
                       @Param("tipo") TipoMovimentacao tipo,
                       @Param("novoStatus") StatusSap novoStatus,
                       @Param("docEntry") Long docEntry,
                       @Param("erro") String erro,
                       @Param("agora") OffsetDateTime agora);

    /** Puts back to PENDENTE what got stuck in ENVIANDO (the instance died mid-send). */
    @Transactional
    @Modifying
    @Query("""
            update Movimentacao m
               set m.sapStatus = br.com.lsk.estoque.domain.model.StatusSap.PENDENTE
             where m.sapStatus = br.com.lsk.estoque.domain.model.StatusSap.ENVIANDO
               and m.sapAtualizadoEm < :limite
            """)
    int liberarPresos(@Param("limite") OffsetDateTime limite);

    @Query("""
            select distinct m.referencia as referencia, m.tipo as tipo
              from Movimentacao m
             where m.sapStatus in (br.com.lsk.estoque.domain.model.StatusSap.PENDENTE,
                                   br.com.lsk.estoque.domain.model.StatusSap.ERRO)
               and m.sapTentativas < :maxTentativas
            """)
    List<ReferenciaPendente> findReferenciasPendentes(@Param("maxTentativas") int maxTentativas, Pageable pageable);
}
