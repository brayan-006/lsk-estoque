package br.com.lsk.inventory.domain.repository;

import br.com.lsk.inventory.domain.model.AbcClass;
import br.com.lsk.inventory.domain.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    /** SELECT ... FOR UPDATE, to serialize operations on the same SKU. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.sku = :sku")
    Optional<Product> fetchForUpdate(@Param("sku") String sku);

    List<Product> findByAbcClassAndActiveTrueOrderBySkuAsc(AbcClass abcClass);

    /** Active products whose sellable balance is at or below the minimum. */
    @Query(value = """
            SELECT p.*
              FROM products p
             WHERE p.active
               AND p.minimum_stock > 0
               AND p.minimum_stock >= COALESCE((
                     SELECT SUM(l.current_quantity)
                       FROM batches l
                      WHERE l.product_id = p.id
                        AND l.status = 'ACTIVE'
                        AND (l.expiry_date IS NULL OR l.expiry_date >= CURRENT_DATE)), 0)
            """, nativeQuery = true)
    List<Product> findBelowMinimum();

    /**
     * ABC by revenue (price x quantity of SALE outflows in the window), with turnover as the tiebreaker.
     * A product is class A while the cumulative share before it is still under the A limit.
     */
    @Modifying
    @Query(value = """
            WITH sales AS (
                SELECT m.product_id,
                       SUM(m.quantity * m.unit_price) AS revenue,
                       SUM(m.quantity)                    AS turnover
                  FROM movements m
                 WHERE m.type = 'OUTBOUND'
                   AND m.source = 'SALE'
                   AND m.unit_price IS NOT NULL
                   AND m.created_at >= :since
                 GROUP BY m.product_id
                HAVING SUM(m.quantity * m.unit_price) > 0
            ), ranking AS (
                SELECT v.product_id,
                       (SUM(v.revenue) OVER (ORDER BY v.revenue DESC, v.turnover DESC, v.product_id
                                                 ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
                        - v.revenue) / SUM(v.revenue) OVER () AS share_previous
                  FROM sales v
            )
            UPDATE products p
               SET abc_class = CASE WHEN r.share_previous < :aLimit THEN 'A'
                                     WHEN r.share_previous < :bLimit THEN 'B'
                                     ELSE 'C' END,
                   updated_at = now()
              FROM ranking r
             WHERE p.id = r.product_id
               AND p.abc_class IS DISTINCT FROM CASE WHEN r.share_previous < :aLimit THEN 'A'
                                                      WHEN r.share_previous < :bLimit THEN 'B'
                                                      ELSE 'C' END
            """, nativeQuery = true)
    int updateClassesWithSales(@Param("since") OffsetDateTime since,
                                  @Param("aLimit") BigDecimal aLimit,
                                  @Param("bLimit") BigDecimal bLimit);

    /** Products without revenue in the window drop to class C. */
    @Modifying
    @Query(value = """
            UPDATE products p
               SET abc_class = 'C', updated_at = now()
             WHERE p.abc_class <> 'C'
               AND NOT EXISTS (
                     SELECT 1
                       FROM movements m
                      WHERE m.product_id = p.id
                        AND m.type = 'OUTBOUND'
                        AND m.source = 'SALE'
                        AND m.unit_price IS NOT NULL
                        AND m.quantity * m.unit_price > 0
                        AND m.created_at >= :since)
            """, nativeQuery = true)
    int downgradeWithoutSales(@Param("since") OffsetDateTime since);
}
