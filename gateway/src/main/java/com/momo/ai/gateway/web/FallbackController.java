package com.momo.ai.gateway.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 熔断降级兜底。返回 200 + 业务错误码，前端可以统一用同一个解析器处理，
 * 避免 502/503 直接打到用户脸上时前端还要单独写一套错误分支。
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/biz")
    public Mono<Void> biz(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":5030,\"message\":\"订单服务暂时不可用，请稍后重试\"}";
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
