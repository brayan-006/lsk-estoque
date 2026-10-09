package br.com.lsk.estoque.application.port;

/** Porta de integração com o ERP (implementada pelo SAP Business One). */
public interface ErpGateway {

    boolean habilitado();

    /** Cria uma Saída de Mercadorias (GoodsIssue) e devolve o DocEntry. Idempotente por referência. */
    Long registrarSaida(DocumentoEstoque documento);

    /** Cria uma Entrada de Mercadorias (GoodsReceipt) e devolve o DocEntry. Idempotente por referência. */
    Long registrarEntrada(DocumentoEstoque documento);
}
