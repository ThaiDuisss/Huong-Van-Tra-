package org.hvtra.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.apigateway.utils.GatewayConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class RequestLoggingFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(GatewayConstants.HEADER_CORRELATION_ID);

        log.info("Request header {}", correlationId);

        return chain.filter(exchange.mutate().build());
    }

    @Override
    public int getOrder() {
        return GatewayConstants.ORDER_LOGGING_FILTER;
    }
}
