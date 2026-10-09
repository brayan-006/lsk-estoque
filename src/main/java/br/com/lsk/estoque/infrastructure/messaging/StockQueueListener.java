package br.com.lsk.estoque.infrastructure.messaging;

import br.com.lsk.estoque.application.dto.VendaEvent;
import br.com.lsk.estoque.application.service.ProcessarVendaService;
import br.com.lsk.estoque.domain.exception.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor idempotente de eventos de venda.
 *
 * <ul>
 *   <li>Sucesso ou duplicado: ack (a idempotência vive no banco, na mesma transação da baixa);</li>
 *   <li>erro de negócio (SKU inexistente, saldo insuficiente): rejeita sem requeue -> DLQ;</li>
 *   <li>erro técnico (banco fora, etc.): propaga; o retry do Spring tenta de novo e, esgotado, vai para a DLQ.</li>
 * </ul>
 * A notificação ao SAP B1 ocorre após o commit (SapSyncTrigger), fora desta thread.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockQueueListener {

    private final ProcessarVendaService processarVenda;

    @RabbitListener(queues = RabbitConfig.FILA_VENDAS)
    public void aoReceberVenda(VendaEvent evento) {
        try {
            var resultado = processarVenda.processar(evento);
            log.info("Evento de venda {} (pedido {}): {}", evento.eventId(), evento.pedido(), resultado);
        } catch (RegraDeNegocioException e) {
            log.error("Evento de venda {} rejeitado (vai para a DLQ): {}", evento.eventId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
