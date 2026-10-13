package br.com.lsk.estoque.application.service;

import br.com.lsk.estoque.application.port.ErpGateway;
import br.com.lsk.estoque.domain.exception.RecursoNaoEncontradoException;
import br.com.lsk.estoque.domain.exception.RegraDeNegocioException;
import br.com.lsk.estoque.domain.model.Movimentacao;
import br.com.lsk.estoque.domain.model.StatusSap;
import br.com.lsk.estoque.domain.repository.MovimentacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SapRetryService {

    private final MovimentacaoRepository movements;
    private final SapSyncService sapSync;
    private final ErpGateway erp;
    private final JdbcTemplate jdbc;

    /** Resets the retry counter of a failed or pending movement and sends it right away. */
    public String retry(long movementId) {
        if (!erp.habilitado()) {
            throw new RegraDeNegocioException("SAP integration is disabled");
        }
        Movimentacao movement = movements.findById(movementId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Movement not found: " + movementId));
        if (movement.getSapStatus() == StatusSap.ENVIADO || movement.getSapStatus() == StatusSap.ENVIANDO) {
            throw new RegraDeNegocioException("Movement is already " + movement.getSapStatus());
        }

        String reference = movement.getReferencia();
        jdbc.update("""
                update movimentacoes set sap_status = 'PENDENTE', sap_tentativas = 0
                 where referencia = ? and tipo = ? and sap_status in ('PENDENTE', 'ERRO')
                """, reference, movement.getTipo().name());
        sapSync.sincronizar(reference, movement.getTipo());
        return reference;
    }
}