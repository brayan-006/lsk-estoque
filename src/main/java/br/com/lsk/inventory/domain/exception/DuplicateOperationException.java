package br.com.lsk.inventory.domain.exception;

public class DuplicateOperationException extends BusinessRuleException {
    public DuplicateOperationException(String message) {
        super(message);
    }
}
