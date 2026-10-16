package br.com.lsk.inventory.application.service;

import br.com.lsk.inventory.application.StockProperties;
import br.com.lsk.inventory.application.port.StockDocument;
import br.com.lsk.inventory.application.port.ErpGateway;
import br.com.lsk.inventory.domain.model.Movement;
import br.com.lsk.inventory.domain.model.Product;
import br.com.lsk.inventory.domain.model.StatusSap;
import br.com.lsk.inventory.domain.model.MovementType;
import br.com.lsk.inventory.domain.repository.MovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sends stored movements to the ERP (simple outbox on the movements table).
 * Deliberately not @Transactional: the HTTP call must not hold a database transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SapSyncService {

    private final MovementRepository movements;
    private final ErpGateway erp;
    private final StockProperties props;

    public void sync(String reference, MovementType type) {
        if (!erp.enabled()) {
            log.debug("SAP integration disabled; {} {} stays PENDING", type, reference);
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        int claimed = movements.claim(reference, type,
                List.of(StatusSap.PENDING, StatusSap.ERROR), props.sapMaxAttempts(), now);
        if (claimed == 0) {
            return; // another thread/instance already took it, it was already sent, or it ran out of attempts
        }

        List<Movement> items = movements.fetchForSend(reference, type, StatusSap.SENDING);
        try {
            StockDocument document = buildDocument(reference, type, items);
            Long docEntry = type == MovementType.OUTBOUND
                    ? erp.registerIssue(document)
                    : erp.registerReceipt(document);
            movements.finishSend(reference, type, StatusSap.SENT, docEntry, null, OffsetDateTime.now());
            log.info("SAP B1: {} {} registered (DocEntry {})", type, reference, docEntry);
        } catch (Exception e) {
            movements.finishSend(reference, type, StatusSap.ERROR, null, truncate(e.getMessage()), OffsetDateTime.now());
            log.error("Failed to send {} {} to SAP B1 (will be retried): {}", type, reference, e.getMessage());
        }
    }

    /** Job entry point: releases stuck sends and retries what is PENDING/ERROR. */
    public void retryPending() {
        if (!erp.enabled()) {
            return;
        }
        movements.releaseStuck(OffsetDateTime.now().minus(props.sapSendTimeout()));
        movements.findPendingReferences(props.sapMaxAttempts(), PageRequest.of(0, 50))
                .forEach(r -> sync(r.getReference(), r.getType()));
    }

    private StockDocument buildDocument(String reference, MovementType type, List<Movement> items) {
        Map<Long, List<Movement>> byProduct = items.stream()
                .collect(Collectors.groupingBy(m -> m.getProduct().getId(), LinkedHashMap::new, Collectors.toList()));

        List<StockDocument.Row> rows = byProduct.values().stream().map(list -> {
            Product product = list.get(0).getProduct();
            BigDecimal quantity = list.stream().map(Movement::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal value = list.stream()
                    .map(m -> m.getQuantity().multiply(m.getUnitCost()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal averageCost = value.divide(quantity, 4, RoundingMode.HALF_UP);

            Map<Long, List<Movement>> byBatch = list.stream()
                    .collect(Collectors.groupingBy(m -> m.getBatch().getId(), LinkedHashMap::new, Collectors.toList()));
            List<StockDocument.Batch> batches = byBatch.values().stream().map(group -> {
                var batch = group.get(0).getBatch();
                BigDecimal qtyBatch = group.stream().map(Movement::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                return new StockDocument.Batch(batch.getBatchCode(), qtyBatch, batch.getManufacturingDate(), batch.getExpiryDate());
            }).toList();

            String itemCode = product.getSapItemCode() != null ? product.getSapItemCode() : product.getSku();
            return new StockDocument.Row(itemCode, quantity, averageCost, batches);
        }).toList();

        String comment = "LSK Inventory | %s | %s".formatted(type, reference);
        return new StockDocument(reference, LocalDate.now(), comment, rows);
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= 480 ? message : message.substring(0, 480);
    }
}
