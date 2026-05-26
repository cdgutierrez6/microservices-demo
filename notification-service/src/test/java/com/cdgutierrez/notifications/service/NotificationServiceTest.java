package com.cdgutierrez.notifications.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for NotificationService.
 *
 * Since the service currently uses log output as its side effect (production
 * integration with SendGrid / Firebase is pending), we verify:
 *   1. All known event types are handled without throwing exceptions.
 *   2. Unknown event types are silently ignored (warn-level log only).
 *   3. Null / empty payloads degrade gracefully (no NPE).
 *   4. Missing individual fields inside the payload degrade gracefully.
 */
class NotificationServiceTest {

    private NotificationService notificationService;
    private ObjectMapper        objectMapper;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
        objectMapper        = new ObjectMapper();
    }

    // ── Known event types ─────────────────────────────────────────────────────

    @Test
    void process_orderCreated_shouldNotThrow() {
        var payload = orderPayload("uuid-order-1", "user-1", "149.99");

        assertThatCode(() -> notificationService.process("ORDER_CREATED", payload))
                .doesNotThrowAnyException();
    }

    @Test
    void process_orderConfirmed_shouldNotThrow() {
        var payload = orderPayload("uuid-order-2", null, null);

        assertThatCode(() -> notificationService.process("ORDER_CONFIRMED", payload))
                .doesNotThrowAnyException();
    }

    @Test
    void process_orderCancelled_shouldNotThrow() {
        var payload = orderPayload("uuid-order-3", null, null);

        assertThatCode(() -> notificationService.process("ORDER_CANCELLED", payload))
                .doesNotThrowAnyException();
    }

    @Test
    void process_userRegistered_shouldNotThrow() {
        var payload = objectMapper.createObjectNode();
        payload.put("userId", "user-42");
        payload.put("email", "cristian@efiziai.com");

        assertThatCode(() -> notificationService.process("USER_REGISTERED", payload))
                .doesNotThrowAnyException();
    }

    // ── Unknown / edge event types ────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN_TYPE", "", "order_created", "null", "ORDER_SHIPPED"})
    void process_unknownEventType_shouldNotThrow(String eventType) {
        var payload = objectMapper.createObjectNode();

        assertThatCode(() -> notificationService.process(eventType, payload))
                .doesNotThrowAnyException();
    }

    @Test
    void process_nullEventType_shouldNotThrow() {
        var payload = objectMapper.createObjectNode();

        assertThatCode(() -> notificationService.process(null, payload))
                .doesNotThrowAnyException();
    }

    // ── Resilience: missing fields in payload ─────────────────────────────────

    @Test
    void process_orderCreated_missingOrderId_shouldNotThrow() {
        var payload = objectMapper.createObjectNode();
        payload.put("userId", "user-1");
        payload.put("totalAmount", "99.00");
        // orderId intentionally missing → path() returns MissingNode → asText() = ""

        assertThatCode(() -> notificationService.process("ORDER_CREATED", payload))
                .doesNotThrowAnyException();
    }

    @Test
    void process_orderCreated_emptyPayload_shouldNotThrow() {
        var empty = objectMapper.createObjectNode();

        assertThatCode(() -> notificationService.process("ORDER_CREATED", empty))
                .doesNotThrowAnyException();
    }

    @Test
    void process_userRegistered_missingEmail_shouldNotThrow() {
        var payload = objectMapper.createObjectNode();
        payload.put("userId", "user-99");
        // email intentionally missing

        assertThatCode(() -> notificationService.process("USER_REGISTERED", payload))
                .doesNotThrowAnyException();
    }

    // ── All event types in a sequence (dispatcher coverage) ──────────────────

    @Test
    void process_allKnownEventTypes_sequentially_shouldNotThrow() {
        var payload = orderPayload("uuid-seq", "user-seq", "50.00");

        String[] knownTypes = {"ORDER_CREATED", "ORDER_CONFIRMED", "ORDER_CANCELLED", "USER_REGISTERED"};
        for (String type : knownTypes) {
            assertThatCode(() -> notificationService.process(type, payload))
                    .as("Event type '%s' should not throw", type)
                    .doesNotThrowAnyException();
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private ObjectNode orderPayload(String orderId, String userId, String totalAmount) {
        var node = objectMapper.createObjectNode();
        if (orderId     != null) node.put("orderId",     orderId);
        if (userId      != null) node.put("userId",      userId);
        if (totalAmount != null) node.put("totalAmount", totalAmount);
        return node;
    }
}
