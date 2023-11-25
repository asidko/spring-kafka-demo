package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
@ConfigurationPropertiesScan
@EnableAsync
public class DemoApplication {

    @ConfigurationProperties(prefix = "spring.kafka.producer.topics")
    record ProducerTopicProperties(
            String personEmailUpdates
    ) {}

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ProducerTopicProperties producerTopics;
    private final PersonEmailUpdatesHandler personEmailUpdatesHandler;

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @SneakyThrows
    public static void sleep(long sec) {
        log.warn("Sleeping for {} sec.", sec);
        Thread.sleep(sec * 1000);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async // use separate thread to not block the main
    public void run() {
        log.warn("<PLAYGROUND> Application is started");

        // Place to play with some code

        sleep(5);

        // Sending some initial message
        log.debug("Sending a message to person-email-updates after application is started");
        kafkaTemplate.send(producerTopics.personEmailUpdates(), """
                {
                  "id": 1001,
                  "email": "john.doe.init@gmail.com"
                }
                """);

        sleep(3);

        // Sending a broken message to test error handling on KafkaListener
        log.debug("Sending a broken message to person-email-updates after application is started");
        kafkaTemplate.send(producerTopics.personEmailUpdates(), """
                {
                  "id": 666,
                  "email": "broken@gmail.com" ^%$LET'S_BRAKE_A_JSON_HERE
                }
                """);

        sleep(1);

        // Sending a good message to test that KafkaListener was restored
        kafkaTemplate.send(producerTopics.personEmailUpdates(), """
                {
                  "id": 1002,
                  "email": "good.one@gmail.com"
                }
                """);
    }

    @KafkaListener(topics = "${spring.kafka.consumer.topics.person-email-updates}")
    public void listen(String message) {
        log.debug("Message was received, payload: {}", message);
        personEmailUpdatesHandler.handle(message);
    }

    @Service
    @RequiredArgsConstructor
    @Slf4j
    public static class PersonEmailUpdatesHandler {
        private final ObjectMapper objectMapper;

        @SneakyThrows
        public void handle(String message) {
            log.debug("Handling started for the input message: {}", message);
            var userData = objectMapper.readValue(message, Map.class);
            log.debug("Handling successfully finished for a user with email: {}", userData.get("email"));
        }
    }
}

