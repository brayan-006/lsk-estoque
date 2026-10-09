package br.com.lsk.estoque.domain.exception;

public class OperacaoDuplicadaException extends RegraDeNegocioException {
    public OperacaoDuplicadaException(String mensagem) {
        super(mensagem);
    }
}
