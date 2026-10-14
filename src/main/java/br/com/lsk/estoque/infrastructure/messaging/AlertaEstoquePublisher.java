package br.com.lsk.estoque.infrastructure.messaging;

import br.com.lsk.estoque.application.event.AlertaEstoqueEvent;
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
public class AlertaEstoquePublisher {

    private final RabbitTemplate rabbitTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(AlertaEstoqueEvent evento) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.RK_ALERTA, evento);
        } catch (AmqpException e) {
            log.error("Could not publish alert {} to RabbitMQ: {}", evento.alertaId(), e.getMessage());
        }
    }
}
