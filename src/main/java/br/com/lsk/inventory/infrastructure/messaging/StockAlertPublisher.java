package br.com.lsk.inventory.infrastructure.messaging;

import br.com.lsk.inventory.application.event.StockAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Publishes the minimum-stock alert to the broker after the commit. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockAlertPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(StockAlertEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_ALERT, event);
        } catch (AmqpException e) {
            log.error("Could not publish alert {} to RabbitMQ: {}", event.alertId(), e.getMessage());
        }
    }
}
