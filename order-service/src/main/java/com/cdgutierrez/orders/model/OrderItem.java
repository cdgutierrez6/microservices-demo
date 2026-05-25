package com.cdgutierrez.orders.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    public static OrderItem of(String productName, BigDecimal unitPrice, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be > 0");
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Unit price must be > 0");
        var item = new OrderItem();
        item.productName = productName;
        item.unitPrice = unitPrice;
        item.quantity = quantity;
        return item;
    }
}
