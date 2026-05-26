# Logging Implementation Plan
## Employee Leave Management Portal

---

## Current State

No proper logging exists in any service. All diagnostic output uses `System.out.println`, which has no log levels, no timestamps, no thread info, and cannot be filtered, redirected, or integrated with log aggregation tools.

### All `System.out.println` Occurrences to Replace

| File | Count | What It Prints |
|------|-------|----------------|
| `notification-service/.../LeaveNotificationConsumer.java` | 15 | Simulated notification event details |
| `leave-management-service/.../EmployeeCreatedConsumer.java` | 3 | RabbitMQ `employee.created` consumption |
| `employee-service/.../EmployeeController.java` | 1 | After publishing `employee.created` event |
| `employee-service/.../EmployeeServiceApplication.java` | 1 | After seeding mock employees |
| `leave-management-service/.../LeaveManagementServiceApplication.java` | 1 | After seeding leave balances |
| `authentication-service/.../AuthenticationServiceApplication.java` | 1 | After seeding mock users |

**Total: 22 `System.out.println` calls to replace with proper SLF4J logging.**

---

## Why No New Dependencies Are Needed

- **SLF4J** (`slf4j-api`) is already on the classpath via `spring-boot-starter` in every service
- **Logback** is Spring Boot's default logging implementation — already bundled
- **`@Slf4j`** is a Lombok annotation — Lombok is already in the parent `pom.xml`

> Zero new Maven dependencies required.

---

## Key Log Events Per Service

### `api-gateway`
| Event | Level |
|-------|-------|
| Incoming request received (method + path) | INFO |
| JWT validation failed | WARN |
| JWT validation succeeded (user + role) | DEBUG |
| Circuit Breaker state transition | WARN |
| Fallback triggered for a service | WARN |

### `authentication-service`
| Event | Level |
|-------|-------|
| Login attempt (username) | INFO |
| Login successful — token issued | INFO |
| Login failed — bad credentials | WARN |
| Mock users seeded at startup | INFO |

### `employee-service`
| Event | Level |
|-------|-------|
| Create employee request received | INFO |
| Employee created successfully | INFO |
| Employee already exists — rejected | WARN |
| `employee.created` event published to RabbitMQ | INFO |
| Mock employees seeded at startup | INFO |
| Access denied (role check failed) | WARN |

### `leave-management-service`
| Event | Level |
|-------|-------|
| Leave application received | INFO |
| Validation failed (past date, overlap, etc.) | WARN |
| Leave applied successfully | INFO |
| Leave approved / rejected / cancelled | INFO |
| `leave.notification` event published | INFO |
| `employee.created` event consumed | INFO |
| Leave balances initialized for new employee | INFO |
| Leave balances already exist — skipped | DEBUG |
| Default balances seeded at startup | INFO |

### `notification-service`
| Event | Level |
|-------|-------|
| `leave.notification` event consumed | INFO |
| Notification details (event type, employee, dates) | INFO |

### `eureka-server`
| Event | Level |
|-------|-------|
| No business logic — Spring's built-in Eureka logs are sufficient | — |

---

## Files to Modify

### 1. `JwtAuthenticationFilter.java` — [MODIFY] `api-gateway`

```java
// Before
return onError(exchange, "No Authorization Header", HttpStatus.UNAUTHORIZED);

// After
@Slf4j
...
log.warn("Request to {} rejected — no Authorization header", path);
return onError(exchange, "No Authorization Header", HttpStatus.UNAUTHORIZED);
```

Full log points to add:
```java
log.debug("Incoming request: {} {}", request.getMethod(), path);
log.warn("JWT validation failed for path: {}", path);
log.debug("JWT validated — userId={}, role={}, username={}", userId, role, username);
```

---

### 2. `FallbackController.java` — [MODIFY] `api-gateway`

```java
// Before
public Mono<ResponseEntity<Map<String, Object>>> authServiceFallback() { ... }

// After
@Slf4j
...
log.warn("Circuit breaker OPEN — fallback triggered for Authentication Service");
```

Add one `log.warn` line at the top of each fallback method.

---

### 3. `AuthController.java` — [MODIFY] `authentication-service`

```java
// Before (AuthenticationServiceApplication.java)
System.out.println("Mock users seeded in Authentication Service.");

// After
log.info("Mock users seeded in Authentication Service.");
```

Add to the controller:
```java
log.info("Login attempt for username: {}", request.getUsername());
log.info("Login successful for username: {}", request.getUsername());
log.warn("Login failed — invalid credentials for username: {}", request.getUsername());
```

---

### 4. `AuthenticationServiceApplication.java` — [MODIFY] `authentication-service`

```java
// Before
System.out.println("Mock users seeded in Authentication Service.");

// After
@Slf4j
...
log.info("Mock users seeded in Authentication Service.");
```

---

### 5. `EmployeeController.java` — [MODIFY] `employee-service`

```java
// Before
System.out.println("Published employee.created event for: " + savedEmployee.getUsername());

// After
@Slf4j
...
log.info("Employee created — id={}, username={}", savedEmployee.getId(), savedEmployee.getUsername());
log.info("Published employee.created event for username: {}", savedEmployee.getUsername());
log.warn("Access denied — role '{}' cannot create employees", userRole);
log.warn("Employee creation rejected — ID {} already exists", employee.getId());
log.warn("Access denied — userId={} attempted to access employee id={}", currentUserId, id);
log.debug("Employee retrieved — id={}", id);
```

---

### 6. `EmployeeServiceApplication.java` — [MODIFY] `employee-service`

```java
// Before
System.out.println("Mock employees seeded and events published.");

// After
log.info("Mock employees seeded and events published.");
```

---

### 7. `EmployeeCreatedConsumer.java` — [MODIFY] `leave-management-service`

```java
// Before
System.out.println("Consuming employee.created event for: " + event.getUsername());
System.out.println("Default leave balances initialized for Employee ID: " + event.getEmployeeId());
System.out.println("Leave balances already initialized for Employee ID: " + event.getEmployeeId());

// After
@Slf4j
...
log.info("Received employee.created event — employeeId={}, username={}", event.getEmployeeId(), event.getUsername());
log.info("Leave balances initialized — employeeId={}", event.getEmployeeId());
log.debug("Leave balances already exist for employeeId={} — skipping", event.getEmployeeId());
```

---

### 8. `LeaveManagementServiceApplication.java` — [MODIFY] `leave-management-service`

```java
// Before
System.out.println("Default leave balances seeded for pre-existing employees.");

// After
log.info("Default leave balances seeded for pre-existing employees.");
```

---

### 9. `LeaveController.java` — [MODIFY] `leave-management-service`

Add at key business events (no existing log statements here):
```java
log.info("Leave application received — employeeId={}, type={}, days={}", employeeId, leaveType, request.getNumberOfDays());
log.warn("Leave rejected — past start date — employeeId={}", employeeId);
log.warn("Leave rejected — insufficient balance — employeeId={}, remaining={}, requested={}", employeeId, balance.getRemaining(), request.getNumberOfDays());
log.warn("Leave rejected — overlapping dates — employeeId={}", employeeId);
log.info("Leave applied successfully — leaveId={}, employeeId={}", savedRequest.getId(), employeeId);
log.info("Leave approved — leaveId={}, managerId={}", id, managerId);
log.info("Leave rejected — leaveId={}, managerId={}", id, managerId);
log.info("Leave cancelled — leaveId={}, employeeId={}", id, employeeId);
log.warn("Forbidden — userId={} (role={}) attempted manager-only operation", managerId, userRole);
log.warn("Leave request not found — id={}", id);
```

---

### 10. `LeaveNotificationConsumer.java` — [MODIFY] `notification-service`

```java
// Before (15 System.out.println lines)
System.out.println("SIMULATED NOTIFICATION LOG ENTRY");
System.out.println("Event Type  : " + event.getEventType());
// ... etc.

// After
@Slf4j
...
log.info("SIMULATED NOTIFICATION — type={}, employeeId={}, leaveId={}, status={}, dates={} to {}",
    event.getEventType(),
    event.getEmployeeId(),
    event.getLeaveId(),
    event.getStatus(),
    event.getStartDate(),
    event.getEndDate()
);
```

This collapses 15 `println` lines into one structured `log.info` statement.

---

### 11. Each service's `application.yml` — [MODIFY] × 5 services

Add logging level configuration to control verbosity per package:

```yaml
logging:
  level:
    root: INFO
    com.niloy: DEBUG          # full DEBUG for your own packages
    org.springframework: WARN # suppress Spring framework noise
    org.hibernate: WARN       # suppress Hibernate SQL noise
```

Add to:
- `api-gateway/src/main/resources/application.yml`
- `authentication-service/src/main/resources/application.yml`
- `employee-service/src/main/resources/application.yml`
- `leave-management-service/src/main/resources/application.yml`
- `notification-service/src/main/resources/application.yml`

---

## How to Use `@Slf4j` (Quick Reference)

Since Lombok is already in the parent `pom.xml`, just add the annotation:

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j           // ← generates: private static final Logger log = LoggerFactory.getLogger(ClassName.class)
@RestController
public class EmployeeController {

    public void someMethod() {
        log.info("Simple message");
        log.info("With params — id={}, name={}", id, name);   // ← preferred over string concat
        log.warn("Warning message");
        log.error("Error occurred", exception);               // ← pass exception as last arg
        log.debug("Debug detail — only shown when level=DEBUG");
    }
}
```

> **Never use string concatenation** in log statements (`"value: " + var`).
> Always use **parameterized logging** (`"value: {}", var`) — it's lazy-evaluated and avoids string allocation when the log level is disabled.

---

## Files Summary

| File | Action | Change |
|------|--------|--------|
| `api-gateway/.../JwtAuthenticationFilter.java` | MODIFY | Add `@Slf4j` + 3 log statements |
| `api-gateway/.../FallbackController.java` | MODIFY | Add `@Slf4j` + 3 `log.warn` (one per fallback) |
| `authentication-service/.../AuthController.java` | MODIFY | Add `@Slf4j` + login flow logs |
| `authentication-service/.../AuthenticationServiceApplication.java` | MODIFY | Replace `println` → `log.info` |
| `employee-service/.../EmployeeController.java` | MODIFY | Replace `println` + add 6 log statements |
| `employee-service/.../EmployeeServiceApplication.java` | MODIFY | Replace `println` → `log.info` |
| `leave-management-service/.../LeaveController.java` | MODIFY | Add 10 log statements |
| `leave-management-service/.../EmployeeCreatedConsumer.java` | MODIFY | Replace 3 `println` → structured logs |
| `leave-management-service/.../LeaveManagementServiceApplication.java` | MODIFY | Replace `println` → `log.info` |
| `notification-service/.../LeaveNotificationConsumer.java` | MODIFY | Replace 15 `println` → 1 structured `log.info` |
| `application.yml` × 5 services | MODIFY | Add `logging:` config block |

**Total: 10 Java files + 5 YAML files = 15 file changes.**

---

## Implementation Order

1. Add `logging:` block to all 5 `application.yml` files
2. `notification-service` — replace 15 `println` with structured log (biggest quick win)
3. `leave-management-service` — `EmployeeCreatedConsumer`, `LeaveController`, `LeaveManagementServiceApplication`
4. `employee-service` — `EmployeeController`, `EmployeeServiceApplication`
5. `authentication-service` — `AuthController`, `AuthenticationServiceApplication`
6. `api-gateway` — `JwtAuthenticationFilter`, `FallbackController`
