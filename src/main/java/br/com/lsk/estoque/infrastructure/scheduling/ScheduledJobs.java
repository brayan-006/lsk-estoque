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
 * Run these on a single instance, or guard them with ShedLock: the SAP sender is
 * concurrency-safe, but the ABC job and the sweeps would run twice.
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
        run("ABC analysis", curvaAbc::recalcular);
    }

    @Scheduled(cron = "${lsk.jobs.lotes-vencidos-cron:0 0 1 * * *}")
    public void marcarLotesVencidos() {
        run("expired batches", () -> {
            int total = lotes.marcarVencidos(LocalDate.now());
            log.info("{} batch(es) marked as VENCIDO", total);
        });
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    public void verificarEstoqueMinimo() {
        run("minimum stock", () -> {
            int abaixo = estoqueMinimo.avaliarTodos();
            log.debug("{} product(s) at or below the minimum stock", abaixo);
        });
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    public void reprocessarSap() {
        run("SAP retry", sapSync::reprocessarPendentes);
    }

    private void run(String nome, Runnable tarefa) {
        try {
            tarefa.run();
        } catch (Exception e) {
            log.error("Job '{}' failed: {}", nome, e.getMessage(), e);
        }
    }
}
