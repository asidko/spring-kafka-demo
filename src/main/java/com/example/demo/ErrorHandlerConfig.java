package com.example.demo;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.util.backoff.ExponentialBackOff;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableScheduling
public class ErrorHandlerConfig {
    private final ApplicationEventPublisher eventPublisher;
    private final MongoTemplate mongoTemplate;

    public record FailedMessageEventData(String message, String topic, Exception error) { }

    @EventListener
    public void onMessageFailedEvent(PayloadApplicationEvent<FailedMessageEventData> event) {
        try {
            log.debug("Saving failed event started, topic: {}", event.getPayload().topic());
            var entity = ErrorHandlerConfig.MessageRequest.fromEventData(event.getPayload());
            var saved = mongoTemplate.insert(entity);
            log.debug("Saving failed event successfully finished, topic: {}, id: {}", saved.topic(), saved.id());
        } catch (DuplicateKeyException ex) {
            log.warn("Saving failed event finished with warning, topic: {}, warning: message already exists in database, details: {}", event.getPayload().topic(), ex.getMessage());
        } catch (Exception ex) {
            log.error("CRITICAL-ERROR. Can't save failed event to database, due to error: {}, message payload: {}", ex.getMessage(), event.getPayload().message(), ex);
        }
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(ConsumerFactory<String, String> consumerFactory) {
        var errorHandler = new DefaultErrorHandler(
                // Setting custom ConsumerRecordRecoverer
                (data, ex) -> {
                    // Log the message for further investigation. This log will be shown after all retries failed
                    log.error("MESSAGE-ERROR. Processing was completely failed for a message:\n{}", data.value(), ex);

                    if (ex instanceof ListenerExecutionFailedException && ex.getCause() instanceof Exception)
                        ex = (Exception) ex.getCause(); // unwrap exception to be closer to the real issue

                    // Throw a Spring Boot event to catch it in another place and store the data
                    eventPublisher.publishEvent(new ErrorHandlerConfig.FailedMessageEventData(String.valueOf(data.value()), data.topic(), ex));
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

    @Document
    public record MessageRequest(
            @Id String id,
            String topic,
            String payload,
            String error,
            // Hash for a topic + payload fields to avoid saving same messages
            @Indexed(unique = true) String hash,
            Instant createdAt
    ) {
        @SneakyThrows
        public static MessageRequest fromEventData(FailedMessageEventData data) {
            var hash = MessageDigest.getInstance("SHA-256").digest((data.message() + data.topic()).getBytes(StandardCharsets.UTF_8));
            return new MessageRequest(null,
                    data.topic(),
                    data.message(),
                    String.valueOf(data.error()),
                    Base64.getEncoder().encodeToString(hash),
                    Instant.now());
        }
    }
}
