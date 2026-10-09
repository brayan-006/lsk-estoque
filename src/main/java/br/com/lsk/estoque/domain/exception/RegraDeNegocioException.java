package br.com.lsk.estoque.domain.exception;

/** Violação de regra de negócio (não adianta tentar de novo sem mudar o dado). */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
