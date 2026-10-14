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
 * Success or duplicate: ack. Business error (unknown SKU, insufficient stock): rejected without requeue, so it
 * goes to the DLQ. Technical error: propagated, so Spring retries it and finally dead-letters it.
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
            log.info("Sale event {} (order {}): {}", evento.eventId(), evento.pedido(), resultado);
        } catch (RegraDeNegocioException e) {
            log.error("Sale event {} rejected (goes to the DLQ): {}", evento.eventId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
