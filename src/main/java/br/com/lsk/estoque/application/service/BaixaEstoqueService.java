package br.com.lsk.estoque.application.service;

import br.com.lsk.estoque.application.dto.LoteBaixado;
import br.com.lsk.estoque.domain.model.Produto;
import br.com.lsk.estoque.domain.repository.BaixaFefoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** FEFO (with FIFO fallback) lives only in pr_dar_baixa_fefo. Requires an open transaction. */
@Service
@RequiredArgsConstructor
public class BaixaEstoqueService {

    private final BaixaFefoRepository baixaFefo;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<LoteBaixado> darBaixa(Produto produto, BigDecimal quantidade, String referencia,
                                      String origem, BigDecimal precoVenda, String documentoOrigem) {
        return baixaFefo.executar(produto, quantidade, referencia, origem, precoVenda, documentoOrigem);
    }
}
