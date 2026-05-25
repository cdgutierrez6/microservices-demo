package com.cdgutierrez.orders;

import com.cdgutierrez.orders.dto.CreateOrderRequest;
import com.cdgutierrez.orders.model.Order;
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

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // inject via reflection since @InjectMocks doesn't inject manually created beans
        try {
            var field = OrderService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(orderService, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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
    void getOrder_whenNotFound_shouldThrowResourceNotFoundException() {
        var id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }
}
