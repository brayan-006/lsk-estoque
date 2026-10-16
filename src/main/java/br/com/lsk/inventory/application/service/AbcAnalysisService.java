package br.com.lsk.inventory.application.service;

import br.com.lsk.inventory.application.StockProperties;
import br.com.lsk.inventory.application.dto.AbcAnalysisResult;
import br.com.lsk.inventory.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AbcAnalysisService {

    private final ProductRepository products;
    private final StockProperties props;

    /** ABC by revenue over the configured window, with turnover as the tiebreaker. */
    @Transactional
    public AbcAnalysisResult recalculate() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime since = now.minusDays(props.abcWindowDays());

        int reclassified = products.updateClassesWithSales(since, props.classALimit(), props.classBLimit());
        int downgraded = products.downgradeWithoutSales(since);

        log.info("ABC analysis recalculated (window {} days): {} reclassified, {} downgraded to C",
                props.abcWindowDays(), reclassified, downgraded);
        return new AbcAnalysisResult(reclassified, downgraded, props.abcWindowDays(), now);
    }
}
