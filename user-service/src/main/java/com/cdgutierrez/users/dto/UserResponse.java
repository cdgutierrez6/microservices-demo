package com.cdgutierrez.users.dto;

import com.cdgutierrez.users.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, boolean active, Instant createdAt) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.isActive(), user.getCreatedAt());
    }

    @SneakyThrows
    public static UserResponse fromJson(String json) {
        return MAPPER.readValue(json, UserResponse.class);
    }

    @SneakyThrows
    public String toJson() {
        return MAPPER.writeValueAsString(this);
    }
}
