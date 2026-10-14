package br.com.lsk.estoque.domain.repository;

import br.com.lsk.estoque.application.dto.LoteBaixado;
import br.com.lsk.estoque.domain.model.Produto;

import java.math.BigDecimal;
import java.util.List;

/** Port for the pr_dar_baixa_fefo function. */
public interface BaixaFefoRepository {

    /** Throws EstoqueInsuficienteException when the balance does not cover the quantity. */
    List<LoteBaixado> executar(Produto produto, BigDecimal quantidade, String referencia,
                               String origem, BigDecimal precoVenda, String documentoOrigem);
}
