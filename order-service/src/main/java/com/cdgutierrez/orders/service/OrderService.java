package com.cdgutierrez.orders.service;

import com.cdgutierrez.orders.dto.CreateOrderRequest;
import com.cdgutierrez.orders.dto.OrderResponse;
import com.cdgutierrez.orders.model.Order;
import com.cdgutierrez.orders.model.OrderItem;
import com.cdgutierrez.orders.model.OutboxEvent;
import com.cdgutierrez.orders.repository.OrderRepository;
import com.cdgutierrez.orders.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        var items = request.items().stream()
                .map(i -> OrderItem.of(i.productName(), i.unitPrice(), i.quantity()))
                .toList();

        var order = Order.create(request.userId(), items);
        orderRepository.save(order);

        // Outbox: persisted in same transaction — guaranteed at-least-once delivery
        outboxRepository.save(OutboxEvent.of(
                order.getId(),
                "ORDER_CREATED",
                toJson(Map.of(
                        "orderId", order.getId().toString(),
                        "userId", order.getUserId().toString(),
                        "totalAmount", order.getTotalAmount().toPlainString(),
                        "status", order.getStatus().name()
                ))
        ));

        log.info("Order {} created for user {}", order.getId(), order.getUserId());
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUser(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(OrderResponse::from).toList();
    }

    @Transactional
    public OrderResponse confirmOrder(UUID orderId) {
        var order = findOrThrow(orderId);
        order.confirm();
        orderRepository.save(order);

        outboxRepository.save(OutboxEvent.of(order.getId(), "ORDER_CONFIRMED",
                toJson(Map.of("orderId", orderId.toString(), "status", "CONFIRMED"))));

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        var order = findOrThrow(orderId);
        order.cancel();
        orderRepository.save(order);

        outboxRepository.save(OutboxEvent.of(order.getId(), "ORDER_CANCELLED",
                toJson(Map.of("orderId", orderId.toString(), "status", "CANCELLED"))));

        return OrderResponse.from(order);
    }

    private Order findOrThrow(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    @SneakyThrows
    private String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }
}
