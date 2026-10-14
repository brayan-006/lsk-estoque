package br.com.lsk.estoque.application.service;

import br.com.lsk.estoque.application.dto.EntradaNfeCommand;
import br.com.lsk.estoque.application.dto.EntradaNfeCommand.ItemEntrada;
import br.com.lsk.estoque.application.dto.ResultadoEntrada;
import br.com.lsk.estoque.application.event.MovimentacaoRegistradaEvent;
import br.com.lsk.estoque.domain.exception.OperacaoDuplicadaException;
import br.com.lsk.estoque.domain.exception.RecursoNaoEncontradoException;
import br.com.lsk.estoque.domain.exception.RegraDeNegocioException;
import br.com.lsk.estoque.domain.model.Lote;
import br.com.lsk.estoque.domain.model.Movimentacao;
import br.com.lsk.estoque.domain.model.Produto;
import br.com.lsk.estoque.domain.model.StatusLote;
import br.com.lsk.estoque.domain.model.TipoMovimentacao;
import br.com.lsk.estoque.domain.repository.IdempotenciaRepository;
import br.com.lsk.estoque.domain.repository.LoteRepository;
import br.com.lsk.estoque.domain.repository.MovimentacaoRepository;
import br.com.lsk.estoque.domain.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntradaEstoqueService {

    private final IdempotenciaRepository idempotencia;
    private final ProdutoRepository produtos;
    private final LoteRepository lotes;
    private final MovimentacaoRepository movimentacoes;
    private final EstoqueMinimoService estoqueMinimo;
    private final ApplicationEventPublisher eventos;

    @Transactional
    public ResultadoEntrada registrarNfe(EntradaNfeCommand cmd) {
        String referencia = "NFE-" + cmd.chaveAcesso();
        if (!idempotencia.registrar(referencia, "NFE")) {
            throw new OperacaoDuplicadaException("NF-e already received: " + cmd.chaveAcesso());
        }

        var itens = cmd.itens().stream()
                .sorted(Comparator.comparing(ItemEntrada::sku).thenComparing(ItemEntrada::codigoLote))
                .toList();

        List<ResultadoEntrada.LoteEntrada> resultado = new ArrayList<>();
        Map<String, Produto> afetados = new LinkedHashMap<>();

        for (ItemEntrada item : itens) {
            validarItem(item);
            Produto produto = afetados.computeIfAbsent(item.sku(), sku -> produtos.buscarParaAtualizar(sku)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Product not found: " + sku)));
            if (!produto.isAtivo()) {
                throw new RegraDeNegocioException("Inactive product: " + item.sku());
            }
            validarValidade(produto, item);

            BigDecimal saldoAntes = lotes.saldoDisponivel(produto.getId());

            Lote lote = lotes.buscarParaAtualizar(produto.getId(), item.codigoLote())
                    .map(existente -> acumular(existente, item))
                    .orElseGet(() -> lotes.save(Lote.builder()
                            .produto(produto)
                            .codigoLote(item.codigoLote())
                            .dataFabricacao(item.dataFabricacao())
                            .dataValidade(item.dataValidade())
                            .quantidadeInicial(item.quantidade())
                            .quantidadeAtual(item.quantidade())
                            .custoUnitario(item.custoUnitario())
                            .nfeChave(cmd.chaveAcesso())
                            .build()));

            produto.setCustoMedio(novoCustoMedio(produto.getCustoMedio(), saldoAntes, item));

            movimentacoes.save(Movimentacao.builder()
                    .produto(produto)
                    .lote(lote)
                    .tipo(TipoMovimentacao.ENTRADA)
                    .quantidade(item.quantidade())
                    .custoUnitario(item.custoUnitario())
                    .referencia(referencia)
                    .origem("NFE")
                    .documentoOrigem(cmd.numero())
                    .build());

            resultado.add(new ResultadoEntrada.LoteEntrada(item.sku(), item.codigoLote(), item.quantidade(),
                    saldoAntes.add(item.quantidade())));
        }

        afetados.values().forEach(estoqueMinimo::avaliar);
        eventos.publishEvent(new MovimentacaoRegistradaEvent(referencia, TipoMovimentacao.ENTRADA));
        log.info("NF-e {} received: {} item(s)", cmd.chaveAcesso(), resultado.size());
        return new ResultadoEntrada(referencia, resultado.size(), resultado);
    }

    private void validarItem(ItemEntrada item) {
        if (item.quantidade() == null || item.quantidade().signum() <= 0) {
            throw new RegraDeNegocioException("Quantity must be greater than zero (SKU " + item.sku() + ")");
        }
        if (item.custoUnitario() == null || item.custoUnitario().signum() < 0) {
            throw new RegraDeNegocioException("Invalid unit cost (SKU " + item.sku() + ")");
        }
    }

    private void validarValidade(Produto produto, ItemEntrada item) {
        if (item.dataValidade() == null && produto.isControlaValidade()) {
            throw new RegraDeNegocioException("SKU %s controls expiry: dataValidade is required for batch %s"
                    .formatted(item.sku(), item.codigoLote()));
        }
        if (item.dataValidade() != null && item.dataValidade().isBefore(LocalDate.now())) {
            throw new RegraDeNegocioException("Batch %s of SKU %s is already expired (%s)"
                    .formatted(item.codigoLote(), item.sku(), item.dataValidade()));
        }
        if (item.dataFabricacao() != null && item.dataValidade() != null
                && item.dataValidade().isBefore(item.dataFabricacao())) {
            throw new RegraDeNegocioException("Expiry before manufacturing date in batch " + item.codigoLote());
        }
    }

    /** Same batch code already exists for the product: adds to the existing quantity. */
    private Lote acumular(Lote lote, ItemEntrada item) {
        if (lote.getStatus() == StatusLote.VENCIDO || lote.getStatus() == StatusLote.BLOQUEADO) {
            throw new RegraDeNegocioException("Batch %s of SKU %s is %s and cannot receive stock"
                    .formatted(lote.getCodigoLote(), item.sku(), lote.getStatus()));
        }
        if (item.dataValidade() != null && lote.getDataValidade() != null
                && !item.dataValidade().equals(lote.getDataValidade())) {
            throw new RegraDeNegocioException("Batch %s already exists with a different expiry (%s)"
                    .formatted(lote.getCodigoLote(), lote.getDataValidade()));
        }
        lote.setQuantidadeInicial(lote.getQuantidadeInicial().add(item.quantidade()));
        lote.setQuantidadeAtual(lote.getQuantidadeAtual().add(item.quantidade()));
        lote.setStatus(StatusLote.ATIVO);
        return lote;
    }

    /** Weighted average cost. */
    private BigDecimal novoCustoMedio(BigDecimal custoAtual, BigDecimal saldoAntes, ItemEntrada item) {
        BigDecimal valorAntes = saldoAntes.multiply(custoAtual);
        BigDecimal valorEntrada = item.quantidade().multiply(item.custoUnitario());
        BigDecimal novoSaldo = saldoAntes.add(item.quantidade());
        return valorAntes.add(valorEntrada).divide(novoSaldo, 4, RoundingMode.HALF_UP);
    }
}
