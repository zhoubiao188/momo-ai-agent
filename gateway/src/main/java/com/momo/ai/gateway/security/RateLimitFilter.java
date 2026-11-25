package com.momo.ai.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关级限流：每 IP 令牌桶。
 *
 * 单机实现，够用于演示；多实例部署时把 Map 换成 Redis + Lua 即可，
 * 算法不变（capacity / refillPerSecond），所以不做过度设计。
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private record Bucket(double tokens, long lastRefillNanos) {
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final double refillPerSecond;

    public RateLimitFilter(@Value("${gateway.rate-limit.capacity:60}") int capacity,
                           @Value("${gateway.rate-limit.refill-per-second:10}") double refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();

        if (!tryAcquire(ip)) {
            log.warn("rate limited: ip={}, path={}", ip, exchange.getRequest().getURI().getPath());
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(
                    "{\"code\":4290,\"message\":\"too many requests\"}".getBytes(StandardCharsets.UTF_8));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
        return chain.filter(exchange);
    }

    boolean tryAcquire(String key) {
        long now = System.nanoTime();
        Bucket updated = buckets.compute(key, (k, old) -> {
            if (old == null) {
                return new Bucket(capacity - 1.0, now);
            }
            double elapsedSeconds = (now - old.lastRefillNanos()) / 1_000_000_000.0;
            double tokens = Math.min(capacity, old.tokens() + elapsedSeconds * refillPerSecond);
            return new Bucket(tokens - 1.0, now);
        });
        if (buckets.size() > 10_000) {
            buckets.clear();
        }
        return updated.tokens() >= 0;
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
