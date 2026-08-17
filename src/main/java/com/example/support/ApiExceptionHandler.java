package com.example.support;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常兜底：任何未处理的异常统一返回友好 JSON，而不是把堆栈抛给客户端。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public Map<String, String> handle(Exception error) {
        log.warn("unhandled exception: {}", error.getMessage());
        return Map.of("error", "服务繁忙，请稍后再试");
    }
}
