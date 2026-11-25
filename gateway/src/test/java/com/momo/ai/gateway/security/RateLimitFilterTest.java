package com.momo.ai.gateway.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    @Test
    void shouldAllowUpToCapacityThenReject() {
        RateLimitFilter filter = new RateLimitFilter(3, 0.001);
        assertThat(filter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(filter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(filter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(filter.tryAcquire("1.1.1.1")).isFalse();
    }

    @Test
    void shouldIsolateDifferentKeys() {
        RateLimitFilter filter = new RateLimitFilter(1, 0.001);
        assertThat(filter.tryAcquire("a")).isTrue();
        assertThat(filter.tryAcquire("a")).isFalse();
        assertThat(filter.tryAcquire("b")).isTrue();
    }
}
