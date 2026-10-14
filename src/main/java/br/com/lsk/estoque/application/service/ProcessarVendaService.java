package br.com.lsk.estoque.application.service;

import br.com.lsk.estoque.application.dto.VendaEvent;
import br.com.lsk.estoque.application.event.MovimentacaoRegistradaEvent;
import br.com.lsk.estoque.domain.exception.RecursoNaoEncontradoException;
import br.com.lsk.estoque.domain.model.Produto;
import br.com.lsk.estoque.domain.model.TipoMovimentacao;
import br.com.lsk.estoque.domain.repository.IdempotenciaRepository;
import br.com.lsk.estoque.domain.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessarVendaService {

    public enum Resultado {PROCESSADO, DUPLICADO}

    private final IdempotenciaRepository idempotencia;
    private final ProdutoRepository produtos;
    private final BaixaEstoqueService baixaEstoque;
    private final EstoqueMinimoService estoqueMinimo;
    private final ApplicationEventPublisher eventos;

    /** One transaction per event: idempotency, FEFO for every item and the minimum-stock check. All or nothing. */
    @Transactional
    public Resultado processar(VendaEvent evento) {
        String referencia = "VENDA-" + evento.eventId();

        if (!idempotencia.registrar(referencia, "VENDA")) {
            log.warn("Sale event {} already processed; ignoring (idempotency)", evento.eventId());
            return Resultado.DUPLICADO;
        }

        // fixed SKU order avoids deadlocks between concurrent events
        var itens = evento.itens().stream()
                .sorted(Comparator.comparing(VendaEvent.ItemVenda::sku))
                .toList();

        Map<String, Produto> afetados = new LinkedHashMap<>();
        for (VendaEvent.ItemVenda item : itens) {
            Produto produto = afetados.computeIfAbsent(item.sku(), sku -> produtos.buscarParaAtualizar(sku)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Product not found: " + sku)));

            var preco = item.precoUnitario() != null ? item.precoUnitario() : produto.getPrecoVenda();
            var baixados = baixaEstoque.darBaixa(produto, item.quantidade(), referencia, "VENDA", preco, evento.pedido());
            log.info("Order {} | SKU {} | deducted {} from {} batch(es): {}", evento.pedido(), item.sku(),
                    item.quantidade(), baixados.size(), baixados);
        }

        afetados.values().forEach(estoqueMinimo::avaliar);
        eventos.publishEvent(new MovimentacaoRegistradaEvent(referencia, TipoMovimentacao.SAIDA));
        return Resultado.PROCESSADO;
    }
}
