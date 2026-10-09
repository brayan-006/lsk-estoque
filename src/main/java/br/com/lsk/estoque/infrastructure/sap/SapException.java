package br.com.lsk.estoque.infrastructure.sap;

public class SapException extends RuntimeException {

    public SapException(String mensagem) {
        super(mensagem);
    }

    public SapException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
