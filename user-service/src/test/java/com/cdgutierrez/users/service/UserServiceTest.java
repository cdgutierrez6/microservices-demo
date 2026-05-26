package com.cdgutierrez.users.service;

import com.cdgutierrez.users.dto.LoginRequest;
import com.cdgutierrez.users.dto.RegisterRequest;
import com.cdgutierrez.users.model.User;
import com.cdgutierrez.users.repository.UserRepository;
import com.cdgutierrez.users.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks private UserService userService;

    private static final String TEST_EMAIL    = "cristian@example.com";
    private static final String TEST_NAME     = "Cristian";
    private static final String TEST_PASSWORD = "SecurePass1!";
    private static final String HASHED_PASS   = "$2a$hashed";

    @BeforeEach
    void setUp() {
        // lenient: register/login tests do not touch Redis; only getById tests do
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    void register_newEmail_shouldSaveUserAndPublishKafkaEvent() {
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(HASHED_PASS);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var request  = new RegisterRequest(TEST_NAME, TEST_EMAIL, TEST_PASSWORD);
        var response = userService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo(TEST_EMAIL);

        verify(userRepository, times(1)).save(any(User.class));
        verify(kafkaTemplate, times(1)).send(eq("user.registered"), anyString(), anyString());
    }

    @Test
    void register_duplicateEmail_shouldThrowIllegalArgumentException() {
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

        var request = new RegisterRequest(TEST_NAME, TEST_EMAIL, TEST_PASSWORD);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void register_shouldHashPasswordBeforeSaving() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(HASHED_PASS);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.register(new RegisterRequest(TEST_NAME, TEST_EMAIL, TEST_PASSWORD));

        verify(passwordEncoder, times(1)).encode(TEST_PASSWORD);
    }

    @Test
    void register_shouldNormalizeEmailToLowercase() {
        var upperEmail = "CRISTIAN@EXAMPLE.COM";
        when(userRepository.existsByEmail(upperEmail.toLowerCase())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn(HASHED_PASS);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.register(new RegisterRequest(TEST_NAME, upperEmail, TEST_PASSWORD));

        verify(userRepository).existsByEmail(upperEmail.toLowerCase());
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_shouldReturnJwtToken() {
        var user = activeUser();
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(TEST_PASSWORD, HASHED_PASS)).thenReturn(true);
        when(jwtService.generate(user.getId(), TEST_EMAIL)).thenReturn("jwt.token.here");

        var response = userService.login(new LoginRequest(TEST_EMAIL, TEST_PASSWORD));

        assertThat(response.token()).isEqualTo("jwt.token.here");
        assertThat(response.userId()).isEqualTo(user.getId());
    }

    @Test
    void login_wrongPassword_shouldThrowIllegalArgumentException() {
        var user = activeUser();
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(TEST_PASSWORD, HASHED_PASS)).thenReturn(false);

        assertThatThrownBy(() -> userService.login(new LoginRequest(TEST_EMAIL, TEST_PASSWORD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void login_unknownEmail_shouldThrowIllegalArgumentException() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(new LoginRequest("ghost@example.com", "any")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void login_inactiveUser_shouldThrowIllegalStateException() {
        var inactiveUser = inactiveUser();
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(inactiveUser));
        when(passwordEncoder.matches(TEST_PASSWORD, HASHED_PASS)).thenReturn(true);

        assertThatThrownBy(() -> userService.login(new LoginRequest(TEST_EMAIL, TEST_PASSWORD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deactivated");
    }

    // ── getById — Cache-Aside ─────────────────────────────────────────────────

    @Test
    void getById_cacheHit_shouldReturnFromRedisWithoutHittingDatabase() {
        var id = UUID.randomUUID();
        // Full JSON with all UserResponse fields — fromJson() is a real static method, no mock needed
        var cachedJson = "{\"id\":\"" + id + "\","
                + "\"name\":\"Cristian\","
                + "\"email\":\"" + TEST_EMAIL + "\","
                + "\"active\":true,"
                + "\"createdAt\":\"2024-01-01T00:00:00Z\"}";
        when(valueOps.get("user:" + id)).thenReturn(cachedJson);

        userService.getById(id);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void getById_cacheMiss_shouldQueryDatabaseAndPopulateCache() {
        var id   = UUID.randomUUID();
        var user = activeUser(id);
        when(valueOps.get("user:" + id)).thenReturn(null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        var response = userService.getById(id);

        assertThat(response.email()).isEqualTo(TEST_EMAIL);
        verify(valueOps, times(1)).set(eq("user:" + id), anyString(), any());
    }

    @Test
    void getById_notFound_shouldThrowResourceNotFoundException() {
        var id = UUID.randomUUID();
        when(valueOps.get("user:" + id)).thenReturn(null);
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User activeUser() {
        return activeUser(UUID.randomUUID());
    }

    private User activeUser(UUID id) {
        return User.builder()
                .id(id)
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .passwordHash(HASHED_PASS)
                .active(true)
                .build();
    }

    private User inactiveUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .passwordHash(HASHED_PASS)
                .active(false)
                .build();
    }
}
