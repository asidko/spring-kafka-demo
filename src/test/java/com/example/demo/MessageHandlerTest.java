package com.example.demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.testcontainers.utility.DockerImageName.parse;

/**
 * Test example for {@link com.example.demo.DemoApplication.PersonEmailUpdatesHandler}
 */
@SpringBootTest(classes = {DemoApplication.class}, properties = {
        // set to 'earliest' so the consumer won't miss the message that was already produced
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@Testcontainers
class MessageHandlerTest {
    @Container
    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer(parse("confluentinc/cp-kafka:7.4.3"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    @SpyBean
    private DemoApplication.PersonEmailUpdatesHandler personEmailUpdatesHandler;
    @Captor
    private ArgumentCaptor<String> dataCaptor;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void testPersonEmailUpdates() {
        // Given
        var topic = "com.example.person.email.events";
        var message1 = """
                { "id": 1002, "email": "first_test@gmail.com" }
                """;

        // When
        kafkaTemplate.send(topic, message1);

        // Then
        await().atMost(Duration.ofMinutes(1)).untilAsserted(() -> {
            // Check that message was processed.
            verify(personEmailUpdatesHandler, atLeastOnce()).handle(dataCaptor.capture());
            // Check that the correct message was processed
            assertThat(dataCaptor.getAllValues()).contains(message1);
        });
    }
}
