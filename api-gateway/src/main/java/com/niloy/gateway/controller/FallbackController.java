package com.niloy.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * FallbackController
 *
 * Handles Circuit Breaker fallback requests forwarded by Spring Cloud Gateway.
 * Each downstream service has its own fallback endpoint so that the error message
 * can be tailored to the specific service that is unavailable.
 *
 * These endpoints are INTERNAL – they are only reachable via
 * "forward:/fallback/<service>" in the gateway route config, not directly from clients.
 */
@Slf4j
@RestController
public class FallbackController {

    // ── Authentication Service Fallback ──────────────────────────────────────

    @RequestMapping("/fallback/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authServiceFallback() {
        log.warn("Circuit breaker OPEN — fallback triggered for Authentication Service");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildErrorBody(
                        "AUTHENTICATION_SERVICE_UNAVAILABLE",
                        "The Authentication Service is currently unavailable. " +
                        "Please try again in a few moments.",
                        "/auth"
                )));
    }

    // ── Employee Service Fallback ─────────────────────────────────────────────

    @RequestMapping("/fallback/employee")
    public Mono<ResponseEntity<Map<String, Object>>> employeeServiceFallback() {
        log.warn("Circuit breaker OPEN — fallback triggered for Employee Service");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildErrorBody(
                        "EMPLOYEE_SERVICE_UNAVAILABLE",
                        "The Employee Service is currently unavailable. " +
                        "Please try again in a few moments.",
                        "/employees"
                )));
    }

    // ── Leave Management Service Fallback ────────────────────────────────────

    @RequestMapping("/fallback/leave")
    public Mono<ResponseEntity<Map<String, Object>>> leaveServiceFallback() {
        log.warn("Circuit breaker OPEN — fallback triggered for Leave Management Service");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildErrorBody(
                        "LEAVE_SERVICE_UNAVAILABLE",
                        "The Leave Management Service is currently unavailable. " +
                        "Your request has not been processed. Please try again later.",
                        "/leaves"
                )));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Map<String, Object> buildErrorBody(String errorCode, String message, String path) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status",    503,
                "error",     "Service Unavailable",
                "code",      errorCode,
                "message",   message,
                "path",      path
        );
    }
}
