package com.cdgutierrez.orders;

import com.cdgutierrez.orders.dto.CreateOrderRequest;
import com.cdgutierrez.orders.model.Order;
import com.cdgutierrez.orders.model.OrderItem;
import com.cdgutierrez.orders.model.OrderStatus;
import com.cdgutierrez.orders.repository.OrderRepository;
import com.cdgutierrez.orders.repository.OutboxRepository;
import com.cdgutierrez.orders.service.OrderService;
import com.cdgutierrez.orders.service.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OutboxRepository outboxRepository;
    @InjectMocks private OrderService orderService;

    @BeforeEach
    void setUp() {
        try {
            var field = OrderService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(orderService, new ObjectMapper());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Order buildPendingOrder() {
        var items = List.of(OrderItem.of("Product A", BigDecimal.TEN, 2));
        return Order.create(UUID.randomUUID(), items);
    }

    // ── createOrder ────────────────────────────────────────────────────────────

    @Test
    void createOrder_withValidRequest_shouldSaveOrderAndOutboxEvent() {
        var request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderRequest.OrderItemRequest("Product A", BigDecimal.TEN, 2))
        );

        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING.name());
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(outboxRepository, times(1)).save(any());
    }

    @Test
    void createOrder_withEmptyItems_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");
    }

    @Test
    void createOrder_withZeroUnitPrice_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> OrderItem.of("A", BigDecimal.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unit price must be > 0");
    }

    @Test
    void createOrder_withZeroQuantity_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> OrderItem.of("A", BigDecimal.TEN, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be > 0");
    }

    // ── getOrder ───────────────────────────────────────────────────────────────

    @Test
    void getOrder_whenFound_shouldReturnOrderResponse() {
        var order = buildPendingOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        var response = orderService.getOrder(order.getId());

        assertThat(response.id()).isEqualTo(order.getId());
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING.name());
    }

    @Test
    void getOrder_whenNotFound_shouldThrowResourceNotFoundException() {
        var id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    // ── getOrdersByUser ────────────────────────────────────────────────────────

    @Test
    void getOrdersByUser_shouldReturnListOfOrders() {
        var userId = UUID.randomUUID();
        var order1 = buildPendingOrder();
        var order2 = buildPendingOrder();
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(order1, order2));

        var result = orderService.getOrdersByUser(userId);

        assertThat(result).hasSize(2);
    }

    @Test
    void getOrdersByUser_whenNoOrders_shouldReturnEmptyList() {
        var userId = UUID.randomUUID();
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        assertThat(orderService.getOrdersByUser(userId)).isEmpty();
    }

    // ── confirmOrder ───────────────────────────────────────────────────────────

    @Test
    void confirmOrder_whenPending_shouldReturnConfirmedResponse() {
        var order = buildPendingOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = orderService.confirmOrder(order.getId());

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED.name());
        verify(outboxRepository).save(any());
    }

    @Test
    void confirmOrder_whenNotFound_shouldThrowResourceNotFoundException() {
        var id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmOrder(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirmOrder_whenAlreadyConfirmed_shouldThrowIllegalStateException() {
        var order = buildPendingOrder();
        order.confirm();  // PENDING -> CONFIRMED
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmOrder(order.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only PENDING orders can be confirmed");
    }

    // ── cancelOrder ────────────────────────────────────────────────────────────

    @Test
    void cancelOrder_whenPending_shouldReturnCancelledResponse() {
        var order = buildPendingOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = orderService.cancelOrder(order.getId());

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED.name());
        verify(outboxRepository).save(any());
    }

    @Test
    void cancelOrder_whenConfirmed_shouldCancelSuccessfully() {
        var order = buildPendingOrder();
        order.confirm();  // PENDING -> CONFIRMED (still cancellable)
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = orderService.cancelOrder(order.getId());

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED.name());
    }

    @Test
    void cancelOrder_whenNotFound_shouldThrowResourceNotFoundException() {
        var id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
