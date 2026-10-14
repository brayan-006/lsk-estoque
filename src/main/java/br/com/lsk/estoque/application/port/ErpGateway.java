package br.com.lsk.estoque.application.port;

/** Port for the ERP integration. Both operations are idempotent by reference and return the DocEntry. */
public interface ErpGateway {

    boolean habilitado();

    Long registrarSaida(DocumentoEstoque documento);

    Long registrarEntrada(DocumentoEstoque documento);
}
