package br.com.lsk.inventory.infrastructure.sap;

public class SapException extends RuntimeException {

    public SapException(String message) {
        super(message);
    }

    public SapException(String message, Throwable cause) {
        super(message, cause);
    }
}
