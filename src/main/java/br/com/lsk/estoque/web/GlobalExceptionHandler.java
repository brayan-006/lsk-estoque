package br.com.lsk.estoque.web;

import br.com.lsk.estoque.domain.exception.EstoqueInsuficienteException;
import br.com.lsk.estoque.domain.exception.OperacaoDuplicadaException;
import br.com.lsk.estoque.domain.exception.RecursoNaoEncontradoException;
import br.com.lsk.estoque.domain.exception.RegraDeNegocioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Maps exceptions to RFC 9457 problem details. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Resource not found", e.getMessage());
    }

    @ExceptionHandler(OperacaoDuplicadaException.class)
    ProblemDetail duplicada(OperacaoDuplicadaException e) {
        return problema(HttpStatus.CONFLICT, "Duplicate operation", e.getMessage());
    }

    @ExceptionHandler(EstoqueInsuficienteException.class)
    ProblemDetail estoqueInsuficiente(EstoqueInsuficienteException e) {
        return problema(HttpStatus.CONFLICT, "Insufficient stock", e.getMessage());
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    ProblemDetail regraDeNegocio(RegraDeNegocioException e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violated", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception e) {
        log.error("Unexpected error", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred.");
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalhe);
        pd.setTitle(titulo);
        return pd;
    }
}
