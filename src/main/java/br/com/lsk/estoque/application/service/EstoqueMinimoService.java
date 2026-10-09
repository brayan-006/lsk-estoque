package br.com.lsk.estoque.application.service;

import br.com.lsk.estoque.application.event.AlertaEstoqueEvent;
import br.com.lsk.estoque.domain.model.AlertaEstoque;
import br.com.lsk.estoque.domain.model.Produto;
import br.com.lsk.estoque.domain.model.TipoAlerta;
import br.com.lsk.estoque.domain.repository.AlertaEstoqueRepository;
import br.com.lsk.estoque.domain.repository.LoteRepository;
import br.com.lsk.estoque.domain.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EstoqueMinimoService {

    private final LoteRepository lotes;
    private final AlertaEstoqueRepository alertas;
    private final ProdutoRepository produtos;
    private final ApplicationEventPublisher eventos;

    /** Avalia um produto: abre alerta se saldo <= mínimo (um por vez) e fecha o alerta se o saldo se recuperou. */
    @Transactional
    public void avaliar(Produto produto) {
        BigDecimal minimo = produto.getEstoqueMinimo();
        if (minimo == null || minimo.signum() <= 0) {
            return;
        }
        BigDecimal saldo = lotes.saldoDisponivel(produto.getId());

        if (saldo.compareTo(minimo) <= 0) {
            if (alertas.existsByProdutoIdAndTipoAndResolvidoFalse(produto.getId(), TipoAlerta.ESTOQUE_MINIMO)) {
                return;
            }
            String mensagem = "Estoque mínimo atingido: SKU %s com saldo %s (mínimo %s)".formatted(
                    produto.getSku(), saldo.stripTrailingZeros().toPlainString(),
                    minimo.stripTrailingZeros().toPlainString());
            AlertaEstoque alerta = alertas.save(AlertaEstoque.builder()
                    .produto(produto)
                    .tipo(TipoAlerta.ESTOQUE_MINIMO)
                    .saldoNoMomento(saldo)
                    .estoqueMinimo(minimo)
                    .mensagem(mensagem)
                    .build());
            log.warn(mensagem);
            eventos.publishEvent(new AlertaEstoqueEvent(alerta.getId(), produto.getSku(), saldo, minimo,
                    mensagem, alerta.getCriadoEm()));
        } else {
            alertas.resolverAbertos(produto.getId(), TipoAlerta.ESTOQUE_MINIMO, OffsetDateTime.now());
        }
    }

    /** Varredura periódica (job): garante alerta mesmo para saldo que caiu por vencimento de lote. */
    public int avaliarTodos() {
        var abaixo = produtos.findAbaixoDoMinimo();
        abaixo.forEach(this::avaliar);
        return abaixo.size();
    }
}
