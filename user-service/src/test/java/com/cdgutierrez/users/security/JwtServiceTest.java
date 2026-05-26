package com.cdgutierrez.users.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    // 32-char secret — meets HMAC-SHA256 minimum key length
    private static final String SECRET        = "test-secret-key-32chars-minimum!";
    private static final long   EXPIRATION_MS = 3_600_000L; // 1 h

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_MS);
    }

    // ── generate ──────────────────────────────────────────────────────────────

    @Test
    void generate_shouldReturnNonNullToken() {
        var token = jwtService.generate(UUID.randomUUID(), "user@example.com");
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void generate_tokenShouldContainCorrectSubject() {
        var userId = UUID.randomUUID();
        var token  = jwtService.generate(userId, "user@example.com");

        Claims claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void generate_tokenShouldContainEmailClaim() {
        var email = "cristian@efiziai.com";
        var token = jwtService.generate(UUID.randomUUID(), email);

        Claims claims = jwtService.parse(token);
        assertThat(claims.get("email", String.class)).isEqualTo(email);
    }

    @Test
    void generate_tokenShouldHaveExpirationInFuture() {
        var token  = jwtService.generate(UUID.randomUUID(), "user@example.com");
        var claims = jwtService.parse(token);

        assertThat(claims.getExpiration()).isInTheFuture();
    }

    @Test
    void generate_twoCallsShouldProduceDifferentTokens() {
        var id    = UUID.randomUUID();
        var email = "user@example.com";

        var token1 = jwtService.generate(id, email);
        var token2 = jwtService.generate(id, email);

        // Timestamps differ by at least 1ms → tokens differ
        assertThat(token1).isNotEqualTo(token2);
    }

    // ── parse ─────────────────────────────────────────────────────────────────

    @Test
    void parse_validToken_shouldReturnClaims() {
        var token  = jwtService.generate(UUID.randomUUID(), "user@example.com");
        var claims = jwtService.parse(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isNotBlank();
    }

    @Test
    void parse_invalidToken_shouldThrowJwtException() {
        assertThatThrownBy(() -> jwtService.parse("this.is.not.a.valid.jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parse_tamperedSignature_shouldThrowJwtException() {
        var token   = jwtService.generate(UUID.randomUUID(), "user@example.com");
        var tampered = token.substring(0, token.lastIndexOf('.') + 1) + "tampered-signature";

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parse_emptyString_shouldThrowException() {
        assertThatThrownBy(() -> jwtService.parse(""))
                .isInstanceOf(Exception.class);
    }

    @Test
    void parse_nullToken_shouldThrowException() {
        assertThatThrownBy(() -> jwtService.parse(null))
                .isInstanceOf(Exception.class);
    }

    // ── isValid ───────────────────────────────────────────────────────────────

    @Test
    void isValid_freshToken_shouldReturnTrue() {
        var token = jwtService.generate(UUID.randomUUID(), "user@example.com");
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void isValid_expiredToken_shouldReturnFalse() {
        // Create service with 1ms expiration → token expires immediately
        var shortLived = new JwtService(SECRET, 1L);
        var token      = shortLived.generate(UUID.randomUUID(), "user@example.com");

        // Small busy-wait ensures token has expired
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertThat(shortLived.isValid(token)).isFalse();
    }

    @Test
    void isValid_randomString_shouldReturnFalse() {
        assertThat(jwtService.isValid("not-a-jwt-at-all")).isFalse();
    }

    @Test
    void isValid_tamperedToken_shouldReturnFalse() {
        var token    = jwtService.generate(UUID.randomUUID(), "user@example.com");
        var tampered = token + "x";

        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void isValid_tokenSignedWithDifferentSecret_shouldReturnFalse() {
        var otherService = new JwtService("different-secret-key-32chars!!!!!", EXPIRATION_MS);
        var foreignToken = otherService.generate(UUID.randomUUID(), "user@example.com");

        assertThat(jwtService.isValid(foreignToken)).isFalse();
    }
}
