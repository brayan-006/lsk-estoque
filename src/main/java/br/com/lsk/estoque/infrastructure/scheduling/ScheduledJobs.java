package br.com.lsk.estoque.infrastructure.scheduling;

import br.com.lsk.estoque.application.service.CurvaAbcService;
import br.com.lsk.estoque.application.service.EstoqueMinimoService;
import br.com.lsk.estoque.application.service.SapSyncService;
import br.com.lsk.estoque.domain.repository.LoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Jobs periódicos. Em ambiente com várias instâncias, proteja com ShedLock (ou rode só em uma instância):
 * o envio ao SAP já é seguro contra concorrência, mas ABC e varreduras seriam executados em duplicidade.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lsk.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledJobs {

    private final CurvaAbcService curvaAbc;
    private final EstoqueMinimoService estoqueMinimo;
    private final SapSyncService sapSync;
    private final LoteRepository lotes;

    @Scheduled(cron = "${lsk.jobs.curva-abc-cron:0 30 2 * * *}")
    public void recalcularCurvaAbc() {
        run("curva ABC", curvaAbc::recalcular);
    }

    @Scheduled(cron = "${lsk.jobs.lotes-vencidos-cron:0 0 1 * * *}")
    public void marcarLotesVencidos() {
        run("lotes vencidos", () -> {
            int total = lotes.marcarVencidos(LocalDate.now());
            log.info("{} lote(s) marcados como VENCIDO", total);
        });
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    public void verificarEstoqueMinimo() {
        run("estoque mínimo", () -> {
            int abaixo = estoqueMinimo.avaliarTodos();
            log.debug("{} produto(s) no estoque mínimo ou abaixo", abaixo);
        });
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    public void reprocessarSap() {
        run("reprocessamento SAP", sapSync::reprocessarPendentes);
    }

    private void run(String nome, Runnable tarefa) {
        try {
            tarefa.run();
        } catch (Exception e) {
            log.error("Job '{}' falhou: {}", nome, e.getMessage(), e);
        }
    }
}
