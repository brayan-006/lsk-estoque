package br.com.lsk.estoque.domain.exception;

public class RecursoNaoEncontradoException extends RegraDeNegocioException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
