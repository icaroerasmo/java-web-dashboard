package com.icaroerasmo.dashboard.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String DETECTION_EXCHANGE = "detection.exchange";
    public static final String DETECTION_ROUTING_KEY = "detection.events";
    public static final String DASHBOARD_DETECTION_QUEUE = "dashboard.detection.events";

    @Bean
    public DirectExchange detectionExchange() {
        return new DirectExchange(DETECTION_EXCHANGE, true, false);
    }

    @Bean
    public Queue dashboardDetectionQueue() {
        return new Queue(DASHBOARD_DETECTION_QUEUE, true);
    }

    @Bean
    public Binding dashboardDetectionBinding(DirectExchange detectionExchange, Queue dashboardDetectionQueue) {
        return BindingBuilder.bind(dashboardDetectionQueue).to(detectionExchange).with(DETECTION_ROUTING_KEY);
    }

    @Bean
    public MessageConverter dashboardMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}