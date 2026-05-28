package com.niloy.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "leave.exchange";
    public static final String QUEUE = "leave.notification.queue";
    public static final String ROUTING_KEY = "leave.notification";

    @Bean
    public Queue leaveQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public TopicExchange leaveExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Binding binding(Queue leaveQueue, TopicExchange leaveExchange) {
        return BindingBuilder.bind(leaveQueue).to(leaveExchange).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter converter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter());
        rabbitTemplate.setObservationEnabled(true);
        return rabbitTemplate;
    }

    /**
     * Explicitly configure the listener container factory with observation enabled.
     * This ensures @RabbitListener methods read the incoming traceparent header from RabbitMQ message
     * headers and create a child span linked to the leave-management-service trace, making the
     * notification-service visible in Jaeger under the same traceId.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter());
        factory.setObservationEnabled(true);
        return factory;
    }
}

