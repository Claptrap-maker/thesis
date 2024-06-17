package ru.ivanova.diplom.logistics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RabbitMQSender {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQSender.class);
    private final AmqpTemplate amqpTemplate;

    @Autowired
    public RabbitMQSender(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    public void send(String queueName, String message, Map<String, Object> headers) {
        MessageProperties messageProperties = new MessageProperties();
        headers.forEach(messageProperties::setHeader);
        Message rabbitMessage = MessageBuilder.withBody(message.getBytes())
                .andProperties(messageProperties)
                .build();

        // Логирование сообщения
        logger.debug("Отправка сообщения в очередь '{}': {}", queueName, message);
        logger.debug("Заголовки: {}", headers);


        amqpTemplate.convertAndSend(queueName, rabbitMessage);
    }
}
