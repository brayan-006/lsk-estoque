package br.com.lsk.inventory.domain.repository;

import br.com.lsk.inventory.domain.model.Batch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    interface ProductBalance {
        Long getProductId();

        BigDecimal getBalance();
    }

    /**
     * Available batches in the order they will be consumed (mirrors the function): with expiry first,
     * then FIFO. Skips expired, blocked and empty batches.
     */
    @Query("""
            select l from Batch l
             where l.product.id = :productId
               and l.status = br.com.lsk.inventory.domain.model.BatchStatus.ACTIVE
               and l.currentQuantity > 0
               and (l.expiryDate is null or l.expiryDate >= current_date)
             order by case when l.expiryDate is null then 1 else 0 end,
                      l.expiryDate asc, l.receivedAt asc, l.id asc
            """)
    List<Batch> findAvailableInFefoOrder(@Param("productId") Long productId);

    /** Pure FIFO order, for products that do not control expiry. */
    @Query("""
            select l from Batch l
             where l.product.id = :productId
               and l.status = br.com.lsk.inventory.domain.model.BatchStatus.ACTIVE
               and l.currentQuantity > 0
             order by l.receivedAt asc, l.id asc
            """)
    List<Batch> findAvailableInFifoOrder(@Param("productId") Long productId);

    /** Active batches expiring up to the given date. */
    @Query("""
            select l from Batch l join fetch l.product
             where l.status = br.com.lsk.inventory.domain.model.BatchStatus.ACTIVE
               and l.currentQuantity > 0
               and l.expiryDate is not null
               and l.expiryDate between current_date and :limit
             order by l.expiryDate asc, l.id asc
            """)
    List<Batch> findExpiringUntil(@Param("limit") LocalDate limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Batch l where l.product.id = :productId and l.batchCode = :code")
    Optional<Batch> fetchForUpdate(@Param("productId") Long productId, @Param("code") String code);

    @Query("""
            select sum(l.currentQuantity) from Batch l
             where l.product.id = :productId
               and l.status = br.com.lsk.inventory.domain.model.BatchStatus.ACTIVE
               and (l.expiryDate is null or l.expiryDate >= current_date)
            """)
    BigDecimal sumAvailable(@Param("productId") Long productId);

    default BigDecimal availableBalance(Long productId) {
        BigDecimal total = sumAvailable(productId);
        return total == null ? BigDecimal.ZERO : total;
    }

    @Query("""
            select l.product.id as productId, sum(l.currentQuantity) as balance
              from Batch l
             where l.product.id in :ids
               and l.status = br.com.lsk.inventory.domain.model.BatchStatus.ACTIVE
               and (l.expiryDate is null or l.expiryDate >= current_date)
             group by l.product.id
            """)
    List<ProductBalance> balancesByProduct(@Param("ids") Collection<Long> ids);

    @Transactional
    @Modifying
    @Query("""
            update Batch l set l.status = br.com.lsk.inventory.domain.model.BatchStatus.EXPIRED
             where l.status = br.com.lsk.inventory.domain.model.BatchStatus.ACTIVE
               and l.expiryDate < :today
            """)
    int markExpired(@Param("today") LocalDate today);
}
