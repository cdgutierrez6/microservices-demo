package com.cdgutierrez.notifications.consumer;

import com.cdgutierrez.notifications.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"orders.created", "orders.updated"},
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("Received event from topic [{}] partition [{}] offset [{}]",
                record.topic(), record.partition(), record.offset());
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String eventType = determineEventType(record.topic(), payload);
            notificationService.process(eventType, payload);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process event from topic {}: {}", record.topic(), ex.getMessage(), ex);
            // Do NOT ack — message goes to retry / DLQ
        }
    }

    @KafkaListener(
            topics = "user.registered",
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserRegistered(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.info("User registered event received: {}", record.key());
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            notificationService.process("USER_REGISTERED", payload);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process user.registered event: {}", ex.getMessage(), ex);
        }
    }

    private String determineEventType(String topic, JsonNode payload) {
        if ("orders.created".equals(topic)) return "ORDER_CREATED";
        String status = payload.path("status").asText("");
        return switch (status) {
            case "CONFIRMED" -> "ORDER_CONFIRMED";
            case "CANCELLED" -> "ORDER_CANCELLED";
            default -> "ORDER_UPDATED";
        };
    }
}
