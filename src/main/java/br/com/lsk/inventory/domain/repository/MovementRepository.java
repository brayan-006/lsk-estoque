package br.com.lsk.inventory.domain.repository;

import br.com.lsk.inventory.domain.model.Movement;
import br.com.lsk.inventory.domain.model.StatusSap;
import br.com.lsk.inventory.domain.model.MovementType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface MovementRepository extends JpaRepository<Movement, Long> {

    interface PendingReference {
        String getReference();

        MovementType getType();
    }

    @Query("""
            select m from Movement m
              join fetch m.product
              join fetch m.batch
             where m.reference = :reference and m.type = :type and m.sapStatus = :status
             order by m.id
            """)
    List<Movement> fetchForSend(@Param("reference") String reference,
                                       @Param("type") MovementType type,
                                       @Param("status") StatusSap status);

    /** Atomically claims the movements of a reference (PENDING/ERROR to SENDING); only one caller gets count > 0. */
    @Transactional
    @Modifying
    @Query("""
            update Movement m
               set m.sapStatus = br.com.lsk.inventory.domain.model.StatusSap.SENDING,
                   m.sapAttempts = m.sapAttempts + 1,
                   m.sapUpdatedAt = :now
             where m.reference = :reference
               and m.type = :type
               and m.sapStatus in (:eligible)
               and m.sapAttempts < :maxAttempts
            """)
    int claim(@Param("reference") String reference,
                    @Param("type") MovementType type,
                    @Param("eligible") Collection<StatusSap> eligible,
                    @Param("maxAttempts") int maxAttempts,
                    @Param("now") OffsetDateTime now);

    @Transactional
    @Modifying
    @Query("""
            update Movement m
               set m.sapStatus = :newStatus,
                   m.sapDocEntry = :docEntry,
                   m.sapError = :error,
                   m.sapUpdatedAt = :now
             where m.reference = :reference
               and m.type = :type
               and m.sapStatus = br.com.lsk.inventory.domain.model.StatusSap.SENDING
            """)
    int finishSend(@Param("reference") String reference,
                       @Param("type") MovementType type,
                       @Param("newStatus") StatusSap newStatus,
                       @Param("docEntry") Long docEntry,
                       @Param("error") String error,
                       @Param("now") OffsetDateTime now);

    /** Puts back to PENDING what got stuck in SENDING (the instance died mid-send). */
    @Transactional
    @Modifying
    @Query("""
            update Movement m
               set m.sapStatus = br.com.lsk.inventory.domain.model.StatusSap.PENDING
             where m.sapStatus = br.com.lsk.inventory.domain.model.StatusSap.SENDING
               and m.sapUpdatedAt < :limit
            """)
    int releaseStuck(@Param("limit") OffsetDateTime limit);

    @Query("""
            select distinct m.reference as reference, m.type as type
              from Movement m
             where m.sapStatus in (br.com.lsk.inventory.domain.model.StatusSap.PENDING,
                                   br.com.lsk.inventory.domain.model.StatusSap.ERROR)
               and m.sapAttempts < :maxAttempts
            """)
    List<PendingReference> findPendingReferences(@Param("maxAttempts") int maxAttempts, Pageable pageable);
}
