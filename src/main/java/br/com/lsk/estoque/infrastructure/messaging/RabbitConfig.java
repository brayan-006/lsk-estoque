package br.com.lsk.estoque.infrastructure.messaging;

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

    public static final String EXCHANGE = "lsk.estoque";
    public static final String DLX = "lsk.estoque.dlx";

    public static final String FILA_VENDAS = "lsk.estoque.vendas.q";
    public static final String FILA_VENDAS_DLQ = "lsk.estoque.vendas.dlq";
    public static final String FILA_ALERTAS = "lsk.estoque.alertas.q";

    public static final String RK_VENDA = "venda.confirmada";
    public static final String RK_ALERTA = "alerta.estoque.minimo";

    @Bean
    TopicExchange estoqueExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue filaVendas() {
        return QueueBuilder.durable(FILA_VENDAS)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(FILA_VENDAS_DLQ)
                .build();
    }

    @Bean
    Queue filaVendasDlq() {
        return QueueBuilder.durable(FILA_VENDAS_DLQ).build();
    }

    @Bean
    Queue filaAlertas() {
        return QueueBuilder.durable(FILA_ALERTAS).build();
    }

    @Bean
    Binding bindingVendas() {
        return BindingBuilder.bind(filaVendas()).to(estoqueExchange()).with(RK_VENDA);
    }

    @Bean
    Binding bindingVendasDlq() {
        return BindingBuilder.bind(filaVendasDlq()).to(deadLetterExchange()).with(FILA_VENDAS_DLQ);
    }

    @Bean
    Binding bindingAlertas() {
        return BindingBuilder.bind(filaAlertas()).to(estoqueExchange()).with(RK_ALERTA);
    }

    /** JSON (Jackson do Spring Boot, já com suporte a java.time) para publicar e consumir. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
