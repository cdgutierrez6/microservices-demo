package com.cdgutierrez.orders.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cdgutierrez.orders.model.OutboxEvent;
import com.cdgutierrez.orders.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    // Outbox Relay — polls every 5 seconds for unpublished events
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        var pending = outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent event : pending) {
            try {
                String topic = resolveTopicForEvent(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                log.info("Published event {} to topic {}", event.getEventType(), topic);
                            } else {
                                log.error("Failed to publish event {}: {}", event.getId(), ex.getMessage());
                            }
                        });
                event.markPublished();
                outboxRepository.save(event);
            } catch (Exception ex) {
                log.error("Error processing outbox event {}: {}", event.getId(), ex.getMessage());
            }
        }
    }

    private String resolveTopicForEvent(String eventType) {
        return switch (eventType) {
            case "ORDER_CREATED" -> KafkaTopics.ORDERS_CREATED;
            case "ORDER_CONFIRMED" -> KafkaTopics.ORDERS_UPDATED;
            case "ORDER_CANCELLED" -> KafkaTopics.ORDERS_UPDATED;
            default -> KafkaTopics.ORDERS_CREATED;
        };
    }
}
