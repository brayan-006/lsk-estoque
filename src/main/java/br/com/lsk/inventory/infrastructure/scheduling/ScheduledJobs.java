package br.com.lsk.inventory.infrastructure.scheduling;

import br.com.lsk.inventory.application.service.AbcAnalysisService;
import br.com.lsk.inventory.application.service.MinimumStockService;
import br.com.lsk.inventory.application.service.SapSyncService;
import br.com.lsk.inventory.domain.repository.BatchRepository;
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

    private final AbcAnalysisService abcAnalysis;
    private final MinimumStockService minimumStock;
    private final SapSyncService sapSync;
    private final BatchRepository batches;

    @Scheduled(cron = "${lsk.jobs.abc-analysis-cron:0 30 2 * * *}")
    public void recalculateAbcAnalysis() {
        run("ABC analysis", abcAnalysis::recalculate);
    }

    @Scheduled(cron = "${lsk.jobs.batches-expired-cron:0 0 1 * * *}")
    public void markExpiredBatches() {
        run("expired batches", () -> {
            int total = batches.markExpired(LocalDate.now());
            log.info("{} batch(es) marked as EXPIRED", total);
        });
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    public void verifyMinimumStock() {
        run("minimum stock", () -> {
            int below = minimumStock.evaluateAll();
            log.debug("{} product(s) at or below the minimum stock", below);
        });
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    public void retrySap() {
        run("SAP retry", sapSync::retryPending);
    }

    private void run(String name, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("Job '{}' failed: {}", name, e.getMessage(), e);
        }
    }
}
