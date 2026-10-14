package br.com.lsk.estoque.infrastructure.sap;

import br.com.lsk.estoque.application.event.MovimentacaoRegistradaEvent;
import br.com.lsk.estoque.application.service.SapSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs after the commit, so the ERP is never told about something that was rolled back.
 * If it fails, the movement stays PENDENTE/ERRO and the retry job picks it up.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SapSyncTrigger {

    private final SapSyncService sapSync;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRegistrarMovimentacao(MovimentacaoRegistradaEvent evento) {
        try {
            sapSync.sincronizar(evento.referencia(), evento.tipo());
        } catch (Exception e) {
            log.error("Unexpected error while syncing {} with SAP: {}", evento.referencia(), e.getMessage(), e);
        }
    }
}
