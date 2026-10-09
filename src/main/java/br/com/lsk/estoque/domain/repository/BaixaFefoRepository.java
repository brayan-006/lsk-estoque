package br.com.lsk.estoque.domain.repository;

import br.com.lsk.estoque.application.dto.LoteBaixado;
import br.com.lsk.estoque.domain.model.Produto;

import java.math.BigDecimal;
import java.util.List;

/** Porta para a stored procedure pr_dar_baixa_fefo. */
public interface BaixaFefoRepository {

    /**
     * @throws br.com.lsk.estoque.domain.exception.EstoqueInsuficienteException se o saldo não cobre a quantidade
     */
    List<LoteBaixado> executar(Produto produto, BigDecimal quantidade, String referencia,
                               String origem, BigDecimal precoVenda, String documentoOrigem);
}
