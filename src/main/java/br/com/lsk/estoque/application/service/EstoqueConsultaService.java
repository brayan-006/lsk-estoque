package br.com.lsk.estoque.application.service;

import br.com.lsk.estoque.application.dto.EstoqueDetalhe;
import br.com.lsk.estoque.application.dto.EstoqueDetalhe.LoteDetalhe;
import br.com.lsk.estoque.application.dto.ProdutoAbcView;
import br.com.lsk.estoque.domain.exception.RecursoNaoEncontradoException;
import br.com.lsk.estoque.domain.model.ClasseAbc;
import br.com.lsk.estoque.domain.model.CriterioBaixa;
import br.com.lsk.estoque.domain.model.Lote;
import br.com.lsk.estoque.domain.model.Produto;
import br.com.lsk.estoque.domain.repository.LoteRepository;
import br.com.lsk.estoque.domain.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EstoqueConsultaService {

    private final ProdutoRepository produtos;
    private final LoteRepository lotes;

    @Transactional(readOnly = true)
    public EstoqueDetalhe consultar(String sku) {
        Produto produto = produtos.findBySku(sku)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Product not found: " + sku));

        List<Lote> disponiveis = lotes.findDisponiveisEmOrdemFefo(produto.getId());
        LocalDate hoje = LocalDate.now();

        List<LoteDetalhe> detalhes = new ArrayList<>();
        BigDecimal saldo = BigDecimal.ZERO;
        int ordem = 1;
        for (Lote lote : disponiveis) {
            saldo = saldo.add(lote.getQuantidadeAtual());
            detalhes.add(new LoteDetalhe(
                    lote.getCodigoLote(),
                    lote.getDataValidade(),
                    lote.getDataEntrada().toLocalDate(),
                    lote.getQuantidadeAtual(),
                    lote.getDataValidade() == null ? CriterioBaixa.PEPS : CriterioBaixa.FEFO,
                    ordem++,
                    lote.getDataValidade() == null ? null : ChronoUnit.DAYS.between(hoje, lote.getDataValidade())));
        }

        boolean abaixo = produto.getEstoqueMinimo().signum() > 0 && saldo.compareTo(produto.getEstoqueMinimo()) <= 0;
        return new EstoqueDetalhe(produto.getSku(), produto.getDescricao(), produto.getUnidade(),
                produto.getClasseAbc(), saldo, produto.getEstoqueMinimo(), abaixo, produto.getCustoMedio(), detalhes);
    }

    @Transactional(readOnly = true)
    public List<ProdutoAbcView> listarPorClasse(ClasseAbc classe) {
        List<Produto> lista = produtos.findByClasseAbcAndAtivoTrueOrderBySkuAsc(classe);
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> saldos = lotes.saldosPorProduto(lista.stream().map(Produto::getId).toList())
                .stream()
                .collect(Collectors.toMap(LoteRepository.SaldoProduto::getProdutoId, LoteRepository.SaldoProduto::getSaldo));

        return lista.stream()
                .map(p -> new ProdutoAbcView(p.getSku(), p.getDescricao(), p.getClasseAbc(), p.getPrecoVenda(),
                        saldos.getOrDefault(p.getId(), BigDecimal.ZERO)))
                .toList();
    }
}
