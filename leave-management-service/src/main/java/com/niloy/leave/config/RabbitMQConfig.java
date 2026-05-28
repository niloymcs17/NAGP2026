package com.niloy.leave.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EMPLOYEE_EXCHANGE = "employee.exchange";
    public static final String EMPLOYEE_QUEUE = "employee.created.queue";
    public static final String EMPLOYEE_ROUTING_KEY = "employee.created";

    public static final String LEAVE_EXCHANGE = "leave.exchange";
    public static final String LEAVE_QUEUE = "leave.notification.queue";
    public static final String LEAVE_ROUTING_KEY = "leave.notification";

    @Bean
    public Queue employeeQueue() {
        return new Queue(EMPLOYEE_QUEUE, true);
    }

    @Bean
    public TopicExchange employeeExchange() {
        return new TopicExchange(EMPLOYEE_EXCHANGE);
    }

    @Bean
    public Binding employeeBinding(Queue employeeQueue, TopicExchange employeeExchange) {
        return BindingBuilder.bind(employeeQueue).to(employeeExchange).with(EMPLOYEE_ROUTING_KEY);
    }

    @Bean
    public Queue leaveQueue() {
        return new Queue(LEAVE_QUEUE, true);
    }

    @Bean
    public TopicExchange leaveExchange() {
        return new TopicExchange(LEAVE_EXCHANGE);
    }

    @Bean
    public Binding leaveBinding(Queue leaveQueue, TopicExchange leaveExchange) {
        return BindingBuilder.bind(leaveQueue).to(leaveExchange).with(LEAVE_ROUTING_KEY);
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
}
