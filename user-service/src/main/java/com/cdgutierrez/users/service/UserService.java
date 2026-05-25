package com.cdgutierrez.users.service;

import com.cdgutierrez.users.dto.*;
import com.cdgutierrez.users.model.User;
import com.cdgutierrez.users.repository.UserRepository;
import com.cdgutierrez.users.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String CACHE_PREFIX = "user:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }

        var user = User.create(request.name(), request.email(), passwordEncoder.encode(request.password()));
        userRepository.save(user);

        kafkaTemplate.send("user.registered",
                user.getId().toString(),
                String.format("{\"userId\":\"%s\",\"email\":\"%s\"}", user.getId(), user.getEmail()));

        log.info("User {} registered successfully", user.getId());
        return UserResponse.from(user);
    }

    public LoginResponse login(LoginRequest request) {
        var user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        if (!user.isActive()) {
            throw new IllegalStateException("Account is deactivated");
        }

        var token = jwtService.generate(user.getId(), user.getEmail());
        return new LoginResponse(token, user.getId(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        // Cache-aside pattern
        String cacheKey = CACHE_PREFIX + id;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for user {}", id);
            return UserResponse.fromJson(cached);
        }

        var user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));

        var response = UserResponse.from(user);
        redisTemplate.opsForValue().set(cacheKey, response.toJson(), CACHE_TTL);
        return response;
    }
}
