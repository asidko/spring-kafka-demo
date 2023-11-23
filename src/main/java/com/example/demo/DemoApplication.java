package com.example.demo;

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
import org.springframework.stereotype.Service;

@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
@ConfigurationPropertiesScan
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
    public static void sleep() {
        Thread.sleep(10000);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        log.info("Application is started");

        // Place to run some code

        new Thread(() -> {
            sleep();
            log.debug("Sending some message to person-email-updates after application is started");
            kafkaTemplate.send(producerTopics.personEmailUpdates(), """
                    {
                      "id": 1001,
                      "email": "john.doe@gmail.com"
                    }
                    """);
        }, "some-msg-thread").start();
    }

    @KafkaListener(topics = "${spring.kafka.consumer.topics.person-email-updates}")
    public void listen(String message) {
        // Check it out in the integration test
        personEmailUpdatesHandler.handle(message);
    }

    @Service
    @Slf4j
    public static class PersonEmailUpdatesHandler {
        public void handle(String message) {
            log.debug("Message received and handled for person-email-updates, message: {}", message);
        }
    }
}
