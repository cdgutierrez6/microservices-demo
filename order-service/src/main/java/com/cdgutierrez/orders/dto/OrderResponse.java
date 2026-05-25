package com.cdgutierrez.orders.dto;

import com.cdgutierrez.orders.model.Order;
import com.cdgutierrez.orders.model.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        String status,
        BigDecimal totalAmount,
        Instant createdAt,
        List<ItemResponse> items
) {
    public record ItemResponse(String productName, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {}

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getItems().stream().map(OrderResponse::mapItem).toList()
        );
    }

    private static ItemResponse mapItem(OrderItem i) {
        return new ItemResponse(
                i.getProductName(),
                i.getUnitPrice(),
                i.getQuantity(),
                i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))
        );
    }
}
