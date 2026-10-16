package br.com.lsk.inventory.application.port;

/** Port for the ERP integration. Both operations are idempotent by reference and return the DocEntry. */
public interface ErpGateway {

    boolean enabled();

    Long registerIssue(StockDocument document);

    Long registerReceipt(StockDocument document);
}
