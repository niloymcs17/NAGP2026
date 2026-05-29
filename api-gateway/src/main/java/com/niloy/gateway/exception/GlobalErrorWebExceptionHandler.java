package com.niloy.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@Order(-2) // higher priority than DefaultErrorWebExceptionHandler
public class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

    public GlobalErrorWebExceptionHandler(ErrorAttributes errorAttributes,
                                          WebProperties webProperties,
                                          ApplicationContext applicationContext,
                                          ServerCodecConfigurer configurer) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        setMessageWriters(configurer.getWriters());
        setMessageReaders(configurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Map<String, Object> error = getErrorAttributes(request, ErrorAttributeOptions.defaults());
        int status = (int) error.getOrDefault("status", 500);
        String message = error.getOrDefault("message", "An unexpected error occurred").toString();
        Throwable throwable = getError(request);

        if (status >= 500) {
            log.error("Unhandled exception at [{}]: {}", request.path(), message, throwable);
        } else {
            log.warn("Request failed at [{}]: status={}, message={}", request.path(), status, message);
        }

        return ServerResponse.status(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "timestamp", Instant.now().toString(),
                        "status",    status,
                        "error",     error.getOrDefault("error", "Error"),
                        "message",   message,
                        "path",      request.path()
                ));
    }
}
