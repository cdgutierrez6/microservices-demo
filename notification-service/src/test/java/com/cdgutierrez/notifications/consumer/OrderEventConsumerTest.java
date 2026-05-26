package com.cdgutierrez.notifications.consumer;

import com.cdgutierrez.notifications.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock private NotificationService notificationService;
    @Mock private Acknowledgment      acknowledgment;

    @InjectMocks private OrderEventConsumer consumer;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        var field = OrderEventConsumer.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(consumer, objectMapper);
    }

    // ── onOrderEvent — orders.created ────────────────────────────────────────

    @Test
    void onOrderEvent_ordersCreated_shouldProcessAndAck() throws Exception {
        var payload = """
                {"orderId":"uuid-1","userId":"user-1","totalAmount":"99.99"}
                """;
        var record = record("orders.created", payload);

        consumer.onOrderEvent(record, acknowledgment);

        verify(notificationService).process(eq("ORDER_CREATED"), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onOrderEvent_ordersCreated_shouldNeverAckOnParseError() {
        var record = record("orders.created", "{ BROKEN JSON {{");

        consumer.onOrderEvent(record, acknowledgment);

        verify(notificationService, never()).process(any(), any());
        verify(acknowledgment, never()).acknowledge();
    }

    // ── onOrderEvent — orders.updated ────────────────────────────────────────

    @Test
    void onOrderEvent_ordersUpdated_confirmedStatus_shouldDispatchOrderConfirmed() throws Exception {
        var payload = """
                {"orderId":"uuid-2","status":"CONFIRMED"}
                """;
        var record = record("orders.updated", payload);

        consumer.onOrderEvent(record, acknowledgment);

        verify(notificationService).process(eq("ORDER_CONFIRMED"), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onOrderEvent_ordersUpdated_cancelledStatus_shouldDispatchOrderCancelled() throws Exception {
        var payload = """
                {"orderId":"uuid-3","status":"CANCELLED"}
                """;
        var record = record("orders.updated", payload);

        consumer.onOrderEvent(record, acknowledgment);

        verify(notificationService).process(eq("ORDER_CANCELLED"), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onOrderEvent_ordersUpdated_unknownStatus_shouldDispatchOrderUpdated() throws Exception {
        var payload = """
                {"orderId":"uuid-4","status":"SHIPPED"}
                """;
        var record = record("orders.updated", payload);

        consumer.onOrderEvent(record, acknowledgment);

        verify(notificationService).process(eq("ORDER_UPDATED"), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onOrderEvent_ordersUpdated_missingStatusField_shouldDispatchOrderUpdated() throws Exception {
        var payload = """
                {"orderId":"uuid-5"}
                """;
        var record = record("orders.updated", payload);

        consumer.onOrderEvent(record, acknowledgment);

        verify(notificationService).process(eq("ORDER_UPDATED"), any());
        verify(acknowledgment).acknowledge();
    }

    // ── onOrderEvent — service exception (downstream) ─────────────────────────

    @Test
    void onOrderEvent_notificationServiceThrows_shouldNotAck() {
        var record = record("orders.created",
                "{\"orderId\":\"uuid-6\",\"userId\":\"u1\",\"totalAmount\":\"10.00\"}");
        doThrow(new RuntimeException("SendGrid unavailable"))
                .when(notificationService).process(any(), any());

        consumer.onOrderEvent(record, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }

    // ── onUserRegistered ──────────────────────────────────────────────────────

    @Test
    void onUserRegistered_validPayload_shouldProcessAndAck() {
        var payload = """
                {"userId":"user-99","email":"new@example.com"}
                """;
        var record  = record("user.registered", payload);

        consumer.onUserRegistered(record, acknowledgment);

        verify(notificationService).process(eq("USER_REGISTERED"), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onUserRegistered_brokenJson_shouldNotAck() {
        var record = record("user.registered", "NOT JSON");

        consumer.onUserRegistered(record, acknowledgment);

        verify(notificationService, never()).process(any(), any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void onUserRegistered_serviceThrows_shouldNotAck() {
        var record = record("user.registered",
                "{\"userId\":\"u9\",\"email\":\"fail@example.com\"}");
        doThrow(new RuntimeException("Email service down"))
                .when(notificationService).process(any(), any());

        consumer.onUserRegistered(record, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> record(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 0L, "key", value);
    }
}
