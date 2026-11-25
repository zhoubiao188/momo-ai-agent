package com.momo.ai.gateway.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService service = new JwtService(
            "momo-ai-agent-demo-secret-key-please-change-me-2026", Duration.ofHours(1));

    @Test
    void shouldIssueAndParse() {
        String token = service.issue("U10086", "值班同学");
        Claims claims = service.parse(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("U10086");
        assertThat(claims.get("name")).isEqualTo("值班同学");
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = service.issue("U1", "x");
        assertThat(service.parse(token + "tampered")).isNull();
    }

    @Test
    void shouldRejectTokenSignedByAnotherKey() {
        JwtService other = new JwtService(
                "another-secret-key-with-enough-length-1234567890", Duration.ofHours(1));
        assertThat(service.parse(other.issue("U1", "x"))).isNull();
    }
}
