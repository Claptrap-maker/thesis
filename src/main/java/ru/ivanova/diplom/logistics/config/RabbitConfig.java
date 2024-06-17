package ru.ivanova.diplom.logistics.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String QUEUE_NAME = "GeoJSONOutput";

    @Bean
    public Queue queue() {
        return new Queue(QUEUE_NAME, true);
    }
}
