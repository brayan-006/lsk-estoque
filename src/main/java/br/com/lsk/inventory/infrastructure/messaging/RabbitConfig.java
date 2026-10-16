package br.com.lsk.inventory.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "lsk.inventory";
    public static final String DLX = "lsk.inventory.dlx";

    public static final String SALES_QUEUE = "lsk.inventory.sales.q";
    public static final String SALES_DLQ = "lsk.inventory.sales.dlq";
    public static final String ALERTS_QUEUE = "lsk.inventory.alerts.q";

    public static final String RK_SALE = "sale.confirmed";
    public static final String RK_ALERT = "alert.stock.minimum";

    @Bean
    TopicExchange stockExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue salesQueue() {
        return QueueBuilder.durable(SALES_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(SALES_DLQ)
                .build();
    }

    @Bean
    Queue salesDlq() {
        return QueueBuilder.durable(SALES_DLQ).build();
    }

    @Bean
    Queue alertsQueue() {
        return QueueBuilder.durable(ALERTS_QUEUE).build();
    }

    @Bean
    Binding bindingSales() {
        return BindingBuilder.bind(salesQueue()).to(stockExchange()).with(RK_SALE);
    }

    @Bean
    Binding bindingSalesDlq() {
        return BindingBuilder.bind(salesDlq()).to(deadLetterExchange()).with(SALES_DLQ);
    }

    @Bean
    Binding bindingAlerts() {
        return BindingBuilder.bind(alertsQueue()).to(stockExchange()).with(RK_ALERT);
    }

    /** JSON converter shared by the publisher and the listeners. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
