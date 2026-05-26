package com.niloy.notification.config;

import org.springframework.amqp.core.*;
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
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter());
        return rabbitTemplate;
    }
}
