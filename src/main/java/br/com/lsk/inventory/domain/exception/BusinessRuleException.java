package br.com.lsk.inventory.domain.exception;

/** Business rule violation: retrying without changing the data will not help. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
