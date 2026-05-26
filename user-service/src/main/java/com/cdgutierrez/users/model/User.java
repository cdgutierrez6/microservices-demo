package com.cdgutierrez.users.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 256)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static User create(String name, String email, String passwordHash) {
        return User.builder()
                .id(UUID.randomUUID())
                .name(name)
                .email(email.toLowerCase().trim())
                .passwordHash(passwordHash)
                .active(true)
                .createdAt(Instant.now())
                .build();
    }

    public void deactivate() {
        this.active = false;
    }
}
