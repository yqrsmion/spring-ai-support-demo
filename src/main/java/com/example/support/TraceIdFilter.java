package com.example.support;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 链路日志：每个请求生成 traceId，返回给客户端（X-Trace-Id 响应头），
 * 并记录请求方法、路径、状态码与耗时，方便排查"哪一步慢"。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        exchange.getResponse().getHeaders().add("X-Trace-Id", traceId);
        return chain.filter(exchange)
                .doOnSuccess(done -> log.info(
                        "traceId={} {} {} status={} costMs={}",
                        traceId,
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI().getPath(),
                        exchange.getResponse().getStatusCode(),
                        System.currentTimeMillis() - start))
                .doOnError(error -> log.warn(
                        "traceId={} {} {} error={} costMs={}",
                        traceId,
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getURI().getPath(),
                        error.getMessage(),
                        System.currentTimeMillis() - start));
    }
}
