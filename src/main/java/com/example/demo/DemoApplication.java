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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.util.backoff.ExponentialBackOff;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
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
    private final ApplicationEventPublisher eventPublisher;
    private final ProducerTopicProperties producerTopics;
    private final PersonEmailUpdatesHandler personEmailUpdatesHandler;
    private final MongoTemplate mongoTemplate;

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
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

    @SneakyThrows
    public static void sleep(long sec) {
        log.warn("Sleeping for {} sec.", sec);
        Thread.sleep(sec * 1000);
    }

    @KafkaListener(topics = "${spring.kafka.consumer.topics.person-email-updates}")
    public void listen(String message) {
        log.debug("Message was received, payload: {}", message);
        personEmailUpdatesHandler.handle(message);
    }

    public record FailedMessageEventData(String message, String topic, Exception error) { }

    @EventListener
    public void onApplicationEvent(PayloadApplicationEvent<FailedMessageEventData> event) {
        try {
            log.debug("Saving failed event started, topic: {}", event.getPayload().topic());
            var entity = MessageRequest.fromEventData(event.getPayload());
            var saved = mongoTemplate.insert(entity);
            log.debug("Saving failed event successfully finished, topic: {}, id: {}", saved.topic(), saved.id());
        } catch (Exception ex) {
            log.error("CRITICAL-ERROR. Can't save failed event to database. Error message: {}", event.getPayload().message());
        }
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(ConsumerFactory<String, String> consumerFactory) {
        var errorHandler = new DefaultErrorHandler(
                (data, ex) -> {
                    // Log the message for further investigation. This log will be shown after all retries failed
                    log.error("MESSAGE-ERROR. Processing was completely failed for a message:\n{}", data.value(), ex);
                    // Throw a Spring Boot event to catch it in another place and store the data
                    eventPublisher.publishEvent(new FailedMessageEventData(String.valueOf(data.value()), data.topic(), ex));
                },
                // Retry with a fixed backoff
                new ExponentialBackOff() {{
                    ////// WARNING ////////////////
                    // You’ll be stuck on the same message, until all retries finished.
                    // Make sure you have at least 2 partitions in a topic and 2 parallel listeners (check listener.concurrency in yaml),
                    // so when one listener is reprocessing, the second one continues working.
                    // Otherwise, configure MaxElapsedTime to a small value.
                    ///////////////////////////////
                    setInitialInterval(1_000); // first retry delay
                    setMaxInterval(4_000);     // max delay
                    setMaxElapsedTime(10_000); // max time for all retries
                    setMultiplier(2.0);        // x2 wait time for each attempt
                }}
        );

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    public record MessageRequest(
            @Id String id, // Ideally, we should save here unique translation id of operation to not duplicate the same messages
            String topic,
            @Indexed boolean succeed,
            String payload,
            String error,
            long retries,
            @Indexed(unique = true) String hash, // Hash for a topic + payload fields to avoid saving same messages
            @CreatedDate String createdAt,
            @LastModifiedDate String updatedAt
    ) {
        @SneakyThrows
        public static MessageRequest fromEventData(FailedMessageEventData data) {
            var hash = MessageDigest.getInstance("SHA-256").digest((data.message() + data.topic()).getBytes(StandardCharsets.UTF_8));
            return new MessageRequest(null, data.topic(), false, data.message(), data.error().getMessage(), 0, Base64.getEncoder().encodeToString(hash), null, null);
        }
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

