package com.example.demo;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Test example for {@link com.example.demo.DemoApplication.PersonEmailUpdatesHandler}
 */
@SpringBootTest(classes = DemoApplication.class)
class MessageHandlerTest {

//    /**
//     * This configuration class is used to enable all Spring Boot beans of a real application
//     */
//    @Configuration
//    public static class IntegrationTestConfiguration {
//        public static void main(String[] args) {
//            SpringApplication.from(DemoApplication::main)
//                    .with(IntegrationTestConfiguration.class)
//                    .run(args);
//        }
//    }

    @SpyBean
    private DemoApplication.PersonEmailUpdatesHandler personEmailUpdatesHandler;

    @Autowired
    private DemoApplication.ProducerTopicProperties producerTopics;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void testPersonEmailUpdates() {
        // Given
        var topic = producerTopics.personEmailUpdates();
        var message = """
                { "id": 1002, "email": "test@gmail.com" }
                """;
        var messageArgumentCaptor = ArgumentCaptor.forClass(String.class);

        // When
        kafkaTemplate.send(topic, message);

        // Then
        // Check that message was processed
        verify(personEmailUpdatesHandler).handle(messageArgumentCaptor.capture());
        // Check that the correct message was processed
        assertThat(messageArgumentCaptor.getValue()).isEqualTo(message);
    }

}
