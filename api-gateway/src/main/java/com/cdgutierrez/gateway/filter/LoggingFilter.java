package com.cdgutierrez.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        log.info("[{}] --> {} {}", correlationId, request.getMethod(), request.getURI().getPath());

        return chain.filter(exchange.mutate()
                .request(request.mutate()
                        .header("X-Correlation-Id", correlationId)
                        .build())
                .build())
                .then(Mono.fromRunnable(() -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("[{}] <-- {} {}ms", correlationId,
                            exchange.getResponse().getStatusCode(), elapsed);
                }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
