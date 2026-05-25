package com.cdgutierrez.orders.kafka;

public final class KafkaTopics {
    public static final String ORDERS_CREATED = "orders.created";
    public static final String ORDERS_UPDATED = "orders.updated";
    public static final String NOTIFICATIONS_PENDING = "notifications.pending";

    private KafkaTopics() {}
}
