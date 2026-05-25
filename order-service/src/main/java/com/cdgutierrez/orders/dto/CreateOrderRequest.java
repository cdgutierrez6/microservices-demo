package com.cdgutierrez.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID userId,
        @NotEmpty @Valid List<OrderItemRequest> items
) {
    public record OrderItemRequest(
            @NotBlank String productName,
            @NotNull @DecimalMin("0.01") BigDecimal unitPrice,
            @Min(1) int quantity
    ) {}
}
