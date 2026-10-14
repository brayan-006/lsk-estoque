package br.com.lsk.estoque.application.service;

import br.com.lsk.estoque.application.EstoqueProperties;
import br.com.lsk.estoque.application.port.DocumentoEstoque;
import br.com.lsk.estoque.application.port.ErpGateway;
import br.com.lsk.estoque.domain.model.Movimentacao;
import br.com.lsk.estoque.domain.model.Produto;
import br.com.lsk.estoque.domain.model.StatusSap;
import br.com.lsk.estoque.domain.model.TipoMovimentacao;
import br.com.lsk.estoque.domain.repository.MovimentacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sends stored movements to the ERP (simple outbox on the movimentacoes table).
 * Deliberately not @Transactional: the HTTP call must not hold a database transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SapSyncService {

    private final MovimentacaoRepository movimentacoes;
    private final ErpGateway erp;
    private final EstoqueProperties props;

    public void sincronizar(String referencia, TipoMovimentacao tipo) {
        if (!erp.habilitado()) {
            log.debug("SAP integration disabled; {} {} stays PENDENTE", tipo, referencia);
            return;
        }
        OffsetDateTime agora = OffsetDateTime.now();
        int reivindicadas = movimentacoes.reivindicar(referencia, tipo,
                List.of(StatusSap.PENDENTE, StatusSap.ERRO), props.sapMaxTentativas(), agora);
        if (reivindicadas == 0) {
            return; // outra thread/instância já pegou, já foi enviado ou estourou as tentativas
        }

        List<Movimentacao> itens = movimentacoes.buscarParaEnvio(referencia, tipo, StatusSap.ENVIANDO);
        try {
            DocumentoEstoque documento = montarDocumento(referencia, tipo, itens);
            Long docEntry = tipo == TipoMovimentacao.SAIDA
                    ? erp.registrarSaida(documento)
                    : erp.registrarEntrada(documento);
            movimentacoes.finalizarEnvio(referencia, tipo, StatusSap.ENVIADO, docEntry, null, OffsetDateTime.now());
            log.info("SAP B1: {} {} registered (DocEntry {})", tipo, referencia, docEntry);
        } catch (Exception e) {
            movimentacoes.finalizarEnvio(referencia, tipo, StatusSap.ERRO, null, truncar(e.getMessage()), OffsetDateTime.now());
            log.error("Failed to send {} {} to SAP B1 (will be retried): {}", tipo, referencia, e.getMessage());
        }
    }

    /** Job entry point: releases stuck sends and retries what is PENDENTE/ERRO. */
    public void reprocessarPendentes() {
        if (!erp.habilitado()) {
            return;
        }
        movimentacoes.liberarPresos(OffsetDateTime.now().minus(props.sapTimeoutEnvio()));
        movimentacoes.findReferenciasPendentes(props.sapMaxTentativas(), PageRequest.of(0, 50))
                .forEach(r -> sincronizar(r.getReferencia(), r.getTipo()));
    }

    private DocumentoEstoque montarDocumento(String referencia, TipoMovimentacao tipo, List<Movimentacao> itens) {
        Map<Long, List<Movimentacao>> porProduto = itens.stream()
                .collect(Collectors.groupingBy(m -> m.getProduto().getId(), LinkedHashMap::new, Collectors.toList()));

        List<DocumentoEstoque.Linha> linhas = porProduto.values().stream().map(lista -> {
            Produto produto = lista.get(0).getProduto();
            BigDecimal quantidade = lista.stream().map(Movimentacao::getQuantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal valor = lista.stream()
                    .map(m -> m.getQuantidade().multiply(m.getCustoUnitario()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal custoMedio = valor.divide(quantidade, 4, RoundingMode.HALF_UP);

            Map<Long, List<Movimentacao>> porLote = lista.stream()
                    .collect(Collectors.groupingBy(m -> m.getLote().getId(), LinkedHashMap::new, Collectors.toList()));
            List<DocumentoEstoque.Lote> lotes = porLote.values().stream().map(movs -> {
                var lote = movs.get(0).getLote();
                BigDecimal qtdLote = movs.stream().map(Movimentacao::getQuantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
                return new DocumentoEstoque.Lote(lote.getCodigoLote(), qtdLote, lote.getDataFabricacao(), lote.getDataValidade());
            }).toList();

            String itemCode = produto.getSapItemCode() != null ? produto.getSapItemCode() : produto.getSku();
            return new DocumentoEstoque.Linha(itemCode, quantidade, custoMedio, lotes);
        }).toList();

        String comentario = "LSK Estoque | %s | %s".formatted(tipo, referencia);
        return new DocumentoEstoque(referencia, LocalDate.now(), comentario, linhas);
    }

    private static String truncar(String mensagem) {
        if (mensagem == null) {
            return "unknown error";
        }
        return mensagem.length() <= 480 ? mensagem : mensagem.substring(0, 480);
    }
}
