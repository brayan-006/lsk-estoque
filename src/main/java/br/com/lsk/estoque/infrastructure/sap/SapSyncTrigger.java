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
 * Dispara o envio ao SAP somente DEPOIS do commit (nunca notifica o ERP de algo que foi revertido)
 * e fora da thread do consumidor RabbitMQ. Se falhar, a movimentação fica PENDENTE/ERRO e o job retenta.
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
            log.error("Erro inesperado ao sincronizar {} com o SAP: {}", evento.referencia(), e.getMessage(), e);
        }
    }
}
