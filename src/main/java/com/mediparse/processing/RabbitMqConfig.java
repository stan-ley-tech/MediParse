package com.mediparse.processing;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * A poison message (one that keeps failing after every in-process retry) is
 * rejected without requeue and lands on the dead-letter queue instead of
 * looping forever, so it stays visible for operators without blocking the
 * documents behind it.
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "mediparse.documents";
    public static final String QUEUE = "mediparse.document-processing";
    public static final String ROUTING_KEY = "process";

    public static final String DEAD_LETTER_EXCHANGE = "mediparse.documents.dlx";
    public static final String DEAD_LETTER_QUEUE = "mediparse.document-processing.dlq";

    @Bean
    public DirectExchange documentsExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public DirectExchange documentsDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue documentProcessingQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                        "x-dead-letter-routing-key", ROUTING_KEY
                ))
                .build();
    }

    @Bean
    public Queue documentProcessingDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding documentProcessingBinding() {
        return BindingBuilder.bind(documentProcessingQueue()).to(documentsExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding documentProcessingDeadLetterBinding() {
        return BindingBuilder.bind(documentProcessingDeadLetterQueue())
                .to(documentsDeadLetterExchange())
                .with(ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
