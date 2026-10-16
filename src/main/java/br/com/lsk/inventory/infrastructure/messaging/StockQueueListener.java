package br.com.lsk.inventory.infrastructure.messaging;

import br.com.lsk.inventory.application.dto.SaleEvent;
import br.com.lsk.inventory.application.service.ProcessSaleService;
import br.com.lsk.inventory.domain.exception.BusinessRuleException;
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

    private final ProcessSaleService processSale;

    @RabbitListener(queues = RabbitConfig.SALES_QUEUE)
    public void onSaleReceived(SaleEvent event) {
        try {
            var result = processSale.process(event);
            log.info("Sale event {} (order {}): {}", event.eventId(), event.order(), result);
        } catch (BusinessRuleException e) {
            log.error("Sale event {} rejected (goes to the DLQ): {}", event.eventId(), e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
