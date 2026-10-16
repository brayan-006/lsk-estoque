package br.com.lsk.inventory.application.dto;

import java.time.OffsetDateTime;

public record AbcAnalysisResult(int reclassifiedProducts, int productsDowngradedToC, int windowDays,
                                OffsetDateTime calculatedEm) {
}
