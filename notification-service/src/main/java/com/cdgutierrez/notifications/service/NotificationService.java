package com.cdgutierrez.notifications.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    public void process(String eventType, JsonNode payload) {
        // Guard: Java switch on null throws NullPointerException
        if (eventType == null) {
            log.warn("Received null event type — ignoring");
            return;
        }
        switch (eventType) {
            case "ORDER_CREATED"   -> notifyOrderCreated(payload);
            case "ORDER_CONFIRMED" -> notifyOrderConfirmed(payload);
            case "ORDER_CANCELLED" -> notifyOrderCancelled(payload);
            case "USER_REGISTERED" -> notifyUserRegistered(payload);
            default                -> log.warn("Unhandled event type: {}", eventType);
        }
    }

    private void notifyOrderCreated(JsonNode payload) {
        String orderId = payload.path("orderId").asText();
        String userId  = payload.path("userId").asText();
        String total   = payload.path("totalAmount").asText();
        // In production: send email/push via SendGrid, Firebase, etc.
        log.info("[NOTIFICATION] Order created — orderId={}, userId={}, total={}", orderId, userId, total);
    }

    private void notifyOrderConfirmed(JsonNode payload) {
        log.info("[NOTIFICATION] Order confirmed — orderId={}", payload.path("orderId").asText());
    }

    private void notifyOrderCancelled(JsonNode payload) {
        log.info("[NOTIFICATION] Order cancelled — orderId={}", payload.path("orderId").asText());
    }

    private void notifyUserRegistered(JsonNode payload) {
        String userId = payload.path("userId").asText();
        String email  = payload.path("email").asText();
        // In production: send welcome email
        log.info("[NOTIFICATION] Welcome email queued for user={} email={}", userId, email);
    }
}
