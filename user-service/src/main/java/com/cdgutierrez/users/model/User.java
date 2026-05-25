package com.cdgutierrez.users.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static User create(String name, String email, String passwordHash) {
        var user = new User();
        user.id = UUID.randomUUID();
        user.name = name;
        user.email = email.toLowerCase().trim();
        user.passwordHash = passwordHash;
        user.active = true;
        user.createdAt = Instant.now();
        return user;
    }

    public void deactivate() {
        this.active = false;
    }
}
