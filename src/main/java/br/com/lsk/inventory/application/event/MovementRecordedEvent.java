package br.com.lsk.inventory.application.event;

import br.com.lsk.inventory.domain.model.MovementType;

/** Published inside the transaction and handled after the commit. */
public record MovementRecordedEvent(String reference, MovementType type) {
}
