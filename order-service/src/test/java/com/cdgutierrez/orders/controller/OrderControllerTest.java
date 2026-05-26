package com.cdgutierrez.orders.controller;

import com.cdgutierrez.orders.dto.CreateOrderRequest;
import com.cdgutierrez.orders.dto.OrderResponse;
import com.cdgutierrez.orders.model.OrderStatus;
import com.cdgutierrez.orders.service.OrderService;
import com.cdgutierrez.orders.service.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper jackson;
    @MockBean  OrderService orderService;

    private OrderResponse sampleResponse(UUID id, String status) {
        return new OrderResponse(id, UUID.randomUUID(), status, BigDecimal.TEN, Instant.now(), List.of());
    }

    // ── POST /api/orders ──────────────────────────────────────────────────────

    @Test
    void createOrder_withValidBody_returns201() throws Exception {
        var id = UUID.randomUUID();
        var request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderRequest.OrderItemRequest("Item A", BigDecimal.TEN, 1))
        );
        when(orderService.createOrder(any())).thenReturn(sampleResponse(id, "PENDING"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jackson.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void createOrder_withNullUserId_returns422() throws Exception {
        var body = "{\"userId\":null,\"items\":[{\"productName\":\"A\",\"unitPrice\":10,\"quantity\":1}]}";
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createOrder_withEmptyItems_returns422() throws Exception {
        var body = "{\"userId\":\"" + UUID.randomUUID() + "\",\"items\":[]}";
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── GET /api/orders/{id} ──────────────────────────────────────────────────

    @Test
    void getOrder_whenFound_returns200() throws Exception {
        var id = UUID.randomUUID();
        when(orderService.getOrder(id)).thenReturn(sampleResponse(id, "PENDING"));

        mockMvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getOrder_whenNotFound_returns404() throws Exception {
        var id = UUID.randomUUID();
        when(orderService.getOrder(id)).thenThrow(new ResourceNotFoundException("Order", id));

        mockMvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/orders/user/{userId} ─────────────────────────────────────────

    @Test
    void getOrdersByUser_returns200WithList() throws Exception {
        var userId = UUID.randomUUID();
        when(orderService.getOrdersByUser(userId))
                .thenReturn(List.of(sampleResponse(UUID.randomUUID(), "PENDING"),
                                    sampleResponse(UUID.randomUUID(), "CONFIRMED")));

        mockMvc.perform(get("/api/orders/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ── PATCH /api/orders/{id}/confirm ────────────────────────────────────────

    @Test
    void confirmOrder_returns200WithConfirmedStatus() throws Exception {
        var id = UUID.randomUUID();
        when(orderService.confirmOrder(id)).thenReturn(sampleResponse(id, "CONFIRMED"));

        mockMvc.perform(patch("/api/orders/{id}/confirm", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmOrder_whenNotPending_returns409() throws Exception {
        var id = UUID.randomUUID();
        when(orderService.confirmOrder(id))
                .thenThrow(new IllegalStateException("Only PENDING orders can be confirmed"));

        mockMvc.perform(patch("/api/orders/{id}/confirm", id))
                .andExpect(status().isConflict());
    }

    // ── PATCH /api/orders/{id}/cancel ─────────────────────────────────────────

    @Test
    void cancelOrder_returns200WithCancelledStatus() throws Exception {
        var id = UUID.randomUUID();
        when(orderService.cancelOrder(id)).thenReturn(sampleResponse(id, "CANCELLED"));

        mockMvc.perform(patch("/api/orders/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_whenShipped_returns409() throws Exception {
        var id = UUID.randomUUID();
        when(orderService.cancelOrder(id))
                .thenThrow(new IllegalStateException("Cannot cancel a shipped or delivered order"));

        mockMvc.perform(patch("/api/orders/{id}/cancel", id))
                .andExpect(status().isConflict());
    }
}
