package br.com.lsk.inventory.application.service;

import br.com.lsk.inventory.application.port.ErpGateway;
import br.com.lsk.inventory.domain.exception.ResourceNotFoundException;
import br.com.lsk.inventory.domain.exception.BusinessRuleException;
import br.com.lsk.inventory.domain.model.Movement;
import br.com.lsk.inventory.domain.model.StatusSap;
import br.com.lsk.inventory.domain.repository.MovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SapRetryService {

    private final MovementRepository movements;
    private final SapSyncService sapSync;
    private final ErpGateway erp;
    private final JdbcTemplate jdbc;

    /** Resets the retry counter of a failed or pending movement and sends it right away. */
    public String retry(long movementId) {
        if (!erp.enabled()) {
            throw new BusinessRuleException("SAP integration is disabled");
        }
        Movement movement = movements.findById(movementId)
                .orElseThrow(() -> new ResourceNotFoundException("Movement not found: " + movementId));
        if (movement.getSapStatus() == StatusSap.SENT || movement.getSapStatus() == StatusSap.SENDING) {
            throw new BusinessRuleException("Movement is already " + movement.getSapStatus());
        }

        String reference = movement.getReference();
        jdbc.update("""
                update movements set sap_status = 'PENDING', sap_attempts = 0
                 where reference = ? and type = ? and sap_status in ('PENDING', 'ERROR')
                """, reference, movement.getType().name());
        sapSync.sync(reference, movement.getType());
        return reference;
    }
}
