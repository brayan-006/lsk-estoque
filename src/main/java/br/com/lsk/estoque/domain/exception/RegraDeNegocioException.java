package br.com.lsk.estoque.domain.exception;

/** Business rule violation: retrying without changing the data will not help. */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
