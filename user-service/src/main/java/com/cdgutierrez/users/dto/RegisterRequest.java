package com.cdgutierrez.users.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) @Pattern(regexp = ".*[A-Z].*", message = "Must contain an uppercase letter")
        @Pattern(regexp = ".*[0-9].*", message = "Must contain a digit") String password
) {}
