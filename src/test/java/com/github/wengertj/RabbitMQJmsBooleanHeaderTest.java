package com.github.wengertj;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.jms.admin.RMQConnectionFactory;
import com.rabbitmq.jms.admin.RMQDestination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {RabbitMQJmsBooleanHeaderTest.ContextConfig.class})
class RabbitMQJmsBooleanHeaderTest {

    private static final String AMQP_EXCHANGE = "amqp.exchange";
    private static final String AMQP_QUEUE = "amqp.queue";
    private static final String AMQP_KEY = "amqp.key";

    @Container
    public static RabbitMQContainer RABBIT_MQ = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management-alpine"))
            .withAdminPassword(null)
            .withExposedPorts(15672, 5672);

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("rabbitmq.host", () -> RABBIT_MQ.getHost());
        registry.add("rabbitmq.port", () -> RABBIT_MQ.getAmqpPort());
    }

    @Autowired
    private JmsTemplate jms;

    @Autowired
    private Destination destination;

    @Autowired
    private Destination amqpDestination;

    @Test
    void nonAmqpMessage() throws JMSException {

        jms.convertAndSend(destination, "{}", message -> {
            message.setBooleanProperty("bool", true);
            return message;
        });


        final Message message = jms.receive(destination);
        assertNotNull(message);
        final boolean bool = message.getBooleanProperty("bool");
        assertTrue(bool, "'bool' property");
    }

    @Test
    void amqpMessage() throws JMSException, IOException, TimeoutException {
        com.rabbitmq.client.ConnectionFactory factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setHost(RABBIT_MQ.getHost());
        factory.setPort(RABBIT_MQ.getAmqpPort());
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(AMQP_EXCHANGE, "topic");
            channel.queueDeclare(AMQP_QUEUE, true, false, false, null);
            channel.queueBind(AMQP_QUEUE, AMQP_EXCHANGE, AMQP_KEY);
        }

        jms.convertAndSend(amqpDestination, "{}", message -> {
            message.setBooleanProperty("bool", true);
            return message;
        });

        final Message message = jms.receive(amqpDestination);
        assertNotNull(message);
        final boolean bool = message.getBooleanProperty("bool");
        // This assertion will fail
        assertTrue(bool, "'bool' property");
    }

    @Configuration
    static class ContextConfig {

        @Bean
        JmsTemplate jmsTemplate(RMQConnectionFactory connectionFactory) {
            JmsTemplate jms =  new JmsTemplate(connectionFactory);
            jms.setReceiveTimeout( 10_000L );
            return jms;
        }

        @Bean
        RMQConnectionFactory jmsFactory(@Value("${rabbitmq.host}") String host, @Value("${rabbitmq.port}") int port) {

            RMQConnectionFactory connectionFactory = new RMQConnectionFactory();
            connectionFactory.setUsername("guest");
            connectionFactory.setPassword("guest");
            connectionFactory.setVirtualHost("/");
            connectionFactory.setHost(host);
            connectionFactory.setPort(port);

            return connectionFactory;
        }

        @Bean
        RMQDestination destination() {

            final RMQDestination dest = new RMQDestination();
            dest.setAmqpQueueName("jms-queue.demo");
            dest.setAmqpExchangeName("jms-exchange.demo");
            dest.setDestinationName("jms-queue.demo");
            dest.setAmqpRoutingKey("jms-queue.demo");
            dest.setAmqp(false);
            return dest;
        }

        @Bean
        RMQDestination amqpDestination() {

            final RMQDestination dest = new RMQDestination();
            dest.setDestinationName("amqp.dest");
            dest.setAmqpExchangeName(AMQP_EXCHANGE);
            dest.setAmqpRoutingKey(AMQP_KEY);
            dest.setAmqpQueueName(AMQP_QUEUE);
            dest.setAmqp(true);
            return dest;

        }
    }

}
