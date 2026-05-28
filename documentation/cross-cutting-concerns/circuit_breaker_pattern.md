# Circuit Breaker Pattern – Implementation in Employee Leave Portal

This document explains how the **Circuit Breaker** pattern is implemented in this project.

---

## Where It Is Implemented

The Circuit Breaker is placed at the **API Gateway** (`api-gateway`, port `8080`) — the single entry point for all client traffic. This means every request from a client passes through the breaker before reaching any downstream microservice.

The dependency was already present in `api-gateway/pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
</dependency>
```

---

## How the Request Flow Works

```
Client (Postman / App)
        │
        ▼
  API Gateway :8080
        │
        ├─ [JwtAuthenticationFilter]  ← validates JWT token (for /employees & /leaves)
        │
        ├─ [CircuitBreakerFilter]     ← monitors failures per service
        │
        │   ┌──────────────┬──────────────────────────────────┐
        │   │  CLOSED      │  Normal – request forwarded       │
        │   │  (healthy)   │  to downstream service            │
        │   └──────────────┴──────────────────────────────────┘
        │
        │   ┌──────────────┬──────────────────────────────────┐
        │   │  OPEN        │  Circuit tripped – request is     │
        │   │  (unhealthy) │  short-circuited, gateway calls   │
        │   │              │  /fallback/<service> immediately   │
        │   └──────────────┴──────────────────────────────────┘
        │
        ▼
  Downstream Microservice  OR  FallbackController (503 JSON)
```

---

## Route → Circuit Breaker → Fallback Mapping

Each gateway route has its own dedicated Circuit Breaker instance and a unique fallback URI:

| Client Route | Downstream Service | CB Instance | Fallback Endpoint |
|--------------|--------------------|-------------|-------------------|
| `/auth/**` | Authentication Service `:8081` | `authServiceCB` | `/fallback/auth` |
| `/employees/**` | Employee Service `:8082` | `employeeServiceCB` | `/fallback/employee` |
| `/leaves/**` | Leave Management Service `:8083` | `leaveServiceCB` | `/fallback/leave` |

This is configured in `application.yml` using Spring Cloud Gateway's `CircuitBreaker` filter:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: authentication-service
          uri: lb://AUTHENTICATION-SERVICE
          predicates:
            - Path=/auth/**
          filters:
            - name: CircuitBreaker
              args:
                name: authServiceCB
                fallbackUri: forward:/fallback/auth

        - id: employee-service
          uri: lb://EMPLOYEE-SERVICE
          predicates:
            - Path=/employees/**
          filters:
            - name: JwtAuthenticationFilter
            - name: CircuitBreaker
              args:
                name: employeeServiceCB
                fallbackUri: forward:/fallback/employee

        - id: leave-management-service
          uri: lb://LEAVE-MANAGEMENT-SERVICE
          predicates:
            - Path=/leaves/**
          filters:
            - name: JwtAuthenticationFilter
            - name: CircuitBreaker
              args:
                name: leaveServiceCB
                fallbackUri: forward:/fallback/leave
```

---

## Resilience4j Configuration

Each Circuit Breaker instance has its own threshold settings in `application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      authServiceCB:                             # Auth is stricter (login is critical)
        registerHealthIndicator: true
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10                    # evaluate over last 10 calls
        minimumNumberOfCalls: 5                  # need at least 5 calls before evaluating
        failureRateThreshold: 50                 # open if >50% fail
        waitDurationInOpenState: 10s             # stay OPEN for 10s before retrying
        permittedNumberOfCallsInHalfOpenState: 3 # allow 3 trial calls in HALF-OPEN
        automaticTransitionFromOpenToHalfOpenEnabled: true

      employeeServiceCB:
        registerHealthIndicator: true
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 20
        minimumNumberOfCalls: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 5s
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true

      leaveServiceCB:
        registerHealthIndicator: true
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 20
        minimumNumberOfCalls: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 5s
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
```

**Why is `authServiceCB` different?**
The Authentication Service has a smaller sliding window (10 calls vs 20) and a longer wait in OPEN state (10 s vs 5 s). Because it handles login — if it is intermittently down, the gateway waits longer before retrying to avoid hammering it repeatedly.

---

## Fallback Controller

When a circuit is OPEN, Spring Cloud Gateway internally forwards the request to `FallbackController.java` instead of the downstream service.

**File:** `api-gateway/src/main/java/com/niloy/gateway/controller/FallbackController.java`

```java
@RequestMapping("/fallback/auth")
public Mono<ResponseEntity<Map<String, Object>>> authServiceFallback() {
    return Mono.just(ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(buildErrorBody(
                    "AUTHENTICATION_SERVICE_UNAVAILABLE",
                    "The Authentication Service is currently unavailable. " +
                    "Please try again in a few moments.",
                    "/auth"
            )));
}
```

There is one `@RequestMapping` method per service (`/fallback/auth`, `/fallback/employee`, `/fallback/leave`). Each returns a `503 Service Unavailable` response with a structured JSON body.

**Sample response when Leave Management Service is down:**
```json
{
  "timestamp": "2026-05-26T13:25:00Z",
  "status": 503,
  "error": "Service Unavailable",
  "code": "LEAVE_SERVICE_UNAVAILABLE",
  "message": "The Leave Management Service is currently unavailable. Your request has not been processed. Please try again later.",
  "path": "/leaves"
}
```

---

## Circuit Breaker States in This Project

| State | What Happens |
|-------|-------------|
| **CLOSED** | Service is healthy. All requests pass through to the downstream microservice normally. |
| **OPEN** | Failure rate exceeded 50%. All requests are immediately short-circuited to `FallbackController`. No calls are made to the downstream service. |
| **HALF-OPEN** | After the `waitDurationInOpenState` (5 s or 10 s), 3 trial requests are sent to the downstream service. If they succeed, the circuit closes. If they fail, it opens again. |

---

## Monitoring Circuit Breaker Health

The Actuator endpoints are exposed so you can inspect the real-time state of each Circuit Breaker:

```
GET http://localhost:8080/actuator/health
```
Returns the health status of all three CB instances (`authServiceCB`, `employeeServiceCB`, `leaveServiceCB`) showing CLOSED, OPEN, or HALF_OPEN.

```
GET http://localhost:8080/actuator/circuitbreakers
```
Returns failure rates, call counts, and current state for each CB instance.

```
GET http://localhost:8080/actuator/circuitbreakerevents
```
Returns a chronological history of state transitions (e.g., CLOSED → OPEN → HALF_OPEN → CLOSED).
