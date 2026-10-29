
package com.kibo.reservation.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "kibo.reservation.events";
    public static final String QUEUE = "kibo.reservation.events.queue";
    public static final String ROUTING_KEY = "reservation.event";

    @Bean
    DirectExchange reservationExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue reservationQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    Binding reservationBinding(
            Queue reservationQueue,
            DirectExchange reservationExchange) {
        return BindingBuilder.bind(reservationQueue)
                .to(reservationExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }
}
