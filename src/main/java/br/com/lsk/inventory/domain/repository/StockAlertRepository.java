package br.com.lsk.inventory.domain.repository;

import br.com.lsk.inventory.domain.model.StockAlert;
import br.com.lsk.inventory.domain.model.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    boolean existsByProductIdAndTypeAndResolvedFalse(Long productId, AlertType type);

    @Transactional
    @Modifying
    @Query("""
            update StockAlert a set a.resolved = true, a.resolvedAt = :now
             where a.product.id = :productId and a.type = :type and a.resolved = false
            """)
    int resolveOpen(@Param("productId") Long productId,
                        @Param("type") AlertType type,
                        @Param("now") OffsetDateTime now);
}
