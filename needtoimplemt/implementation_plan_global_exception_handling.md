# Global Exception Handling – Implementation Plan
## Employee Leave Management Portal

---

## Current Problem

Error handling is scattered **manually inside every controller method** as inline `ResponseEntity` returns with plain string bodies. There is no safety net for uncaught exceptions.

### Current Anti-Pattern (repeated 20+ times across the codebase)
```java
// LeaveController.java – inconsistent, ad-hoc error returns
return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Start date cannot be in the past");
return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Leave request not found");
return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only managers can approve leave requests");

// EmployeeController.java
return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found");
return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only managers can create employees");
```

### Problems with the Current Approach

| Problem | Impact |
|---------|--------|
| Plain string error bodies | Client gets `"Leave request not found"` (a string), not a JSON object — breaks client-side parsing |
| No standard error shape | Every error looks different; no `timestamp`, `path`, or `code` field |
| No uncaught exception safety | Unexpected `NullPointerException` or DB error returns a raw Spring Whitelabel HTML error page |
| No `@Valid` / request body validation | Invalid JSON fields silently ignored or cause 500 errors |
| Business logic mixed with HTTP concerns | Controllers are bloated — validation, business rules, and HTTP error formatting all in one place |

---

## Target Error Response Format

After implementation, **every error** from every service will return this consistent JSON shape:

```json
{
  "timestamp": "2026-05-26T14:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Leave request not found",
  "path": "/leaves/99/approve"
}
```

---

## Services in Scope

| Service | Controller File | Needs `@RestControllerAdvice` |
|---------|----------------|-------------------------------|
| `employee-service` | `EmployeeController.java` | ✅ Yes |
| `leave-management-service` | `LeaveController.java` | ✅ Yes (highest priority — 20+ inline errors) |
| `authentication-service` | `AuthController.java` | ✅ Yes |
| `api-gateway` | `FallbackController.java` | ✅ Yes (reactive / WebFlux variant) |
| `notification-service` | No REST controller | ⬜ Low priority (consumer only) |
| `eureka-server` | No business REST controller | ⬜ Not needed |

---

## Files to Create / Modify

---

### Step 1 – Create a shared `ErrorResponse` model per service

Each service gets its own `ErrorResponse` record/class inside an `exception` package.

**File to create** (repeat for each service, same content):
`<service>/src/main/java/com/niloy/<module>/exception/ErrorResponse.java`

```java
package com.niloy.<module>.exception;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path);
    }
}
```

Create for:
- `com.niloy.employee.exception.ErrorResponse`
- `com.niloy.leave.exception.ErrorResponse`
- `com.niloy.auth.exception.ErrorResponse`

---

### Step 2 – Create custom domain exceptions per service

Replace raw `orElse(null)` + null-checks with meaningful exception classes.

**Files to create:**

#### `employee-service`
`com/niloy/employee/exception/EmployeeNotFoundException.java`
```java
package com.niloy.employee.exception;

public class EmployeeNotFoundException extends RuntimeException {
    public EmployeeNotFoundException(Long id) {
        super("Employee not found with ID: " + id);
    }
}
```

`com/niloy/employee/exception/EmployeeAlreadyExistsException.java`
```java
package com.niloy.employee.exception;

public class EmployeeAlreadyExistsException extends RuntimeException {
    public EmployeeAlreadyExistsException(Long id) {
        super("Employee already exists with ID: " + id);
    }
}
```

`com/niloy/employee/exception/AccessDeniedException.java`
```java
package com.niloy.employee.exception;

public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
```

#### `leave-management-service`
`com/niloy/leave/exception/LeaveRequestNotFoundException.java`
`com/niloy/leave/exception/InsufficientLeaveBalanceException.java`
`com/niloy/leave/exception/InvalidLeaveRequestException.java`
`com/niloy/leave/exception/AccessDeniedException.java`

#### `authentication-service`
`com/niloy/auth/exception/InvalidCredentialsException.java`

---

### Step 3 – Create `GlobalExceptionHandler` per service [MOST IMPORTANT]

#### For `employee-service` and `leave-management-service` (standard Spring MVC)

`<service>/src/main/java/com/niloy/<module>/exception/GlobalExceptionHandler.java`

```java
package com.niloy.<module>.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 – Resource not found
    @ExceptionHandler({
        EmployeeNotFoundException.class,
        LeaveRequestNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(404, "Not Found", ex.getMessage(), req.getRequestURI()));
    }

    // 400 – Bad request / business rule violation
    @ExceptionHandler({
        InsufficientLeaveBalanceException.class,
        InvalidLeaveRequestException.class,
        EmployeeAlreadyExistsException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(400, "Bad Request", ex.getMessage(), req.getRequestURI()));
    }

    // 403 – Forbidden / role-based access
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of(403, "Forbidden", ex.getMessage(), req.getRequestURI()));
    }

    // 400 – @Valid / @RequestBody validation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(400, "Validation Failed", message, req.getRequestURI()));
    }

    // 500 – Catch-all for unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(500, "Internal Server Error",
                "An unexpected error occurred. Please try again later.", req.getRequestURI()));
    }
}
```

#### For `api-gateway` (Reactive / WebFlux)

`api-gateway/src/main/java/com/niloy/gateway/exception/GlobalErrorWebExceptionHandler.java`

```java
package com.niloy.gateway.exception;

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

@Component
@Order(-2)  // higher priority than DefaultErrorWebExceptionHandler
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
        return ServerResponse.status(HttpStatus.valueOf(status))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "timestamp", Instant.now().toString(),
                "status",    status,
                "error",     error.getOrDefault("error", "Error"),
                "message",   error.getOrDefault("message", "An unexpected error occurred"),
                "path",      request.path()
            ));
    }
}
```

---

### Step 4 – Refactor Controllers to use custom exceptions

Replace the inline `orElse(null)` + null-check + `ResponseEntity.status(...).body("string")` pattern with clean throws.

#### Before (`EmployeeController.java`):
```java
Employee employee = employeeRepository.findById(id).orElse(null);
if (employee == null) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found");
}
```

#### After:
```java
Employee employee = employeeRepository.findById(id)
    .orElseThrow(() -> new EmployeeNotFoundException(id));
```

#### Before (`LeaveController.java`):
```java
if (!userRole.equals("MANAGER")) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only managers can approve leave requests");
}
```

#### After:
```java
if (!userRole.equals("MANAGER")) {
    throw new AccessDeniedException("Only managers can approve leave requests");
}
```

The `GlobalExceptionHandler` automatically catches these and returns the standard JSON shape.

---

## Files Summary

| File | Action | Service |
|------|--------|---------|
| `exception/ErrorResponse.java` | CREATE | employee, leave, auth |
| `exception/EmployeeNotFoundException.java` | CREATE | employee |
| `exception/EmployeeAlreadyExistsException.java` | CREATE | employee |
| `exception/AccessDeniedException.java` | CREATE | employee, leave |
| `exception/LeaveRequestNotFoundException.java` | CREATE | leave |
| `exception/InsufficientLeaveBalanceException.java` | CREATE | leave |
| `exception/InvalidLeaveRequestException.java` | CREATE | leave |
| `exception/InvalidCredentialsException.java` | CREATE | auth |
| `exception/GlobalExceptionHandler.java` | CREATE | employee, leave, auth |
| `exception/GlobalErrorWebExceptionHandler.java` | CREATE | api-gateway |
| `controller/EmployeeController.java` | REFACTOR | employee |
| `controller/LeaveController.java` | REFACTOR | leave |
| `controller/AuthController.java` | REFACTOR | auth |

---

## Before vs. After Comparison

### Before (current) — Employee not found:
```
HTTP 404
Content-Type: text/plain

Employee not found
```

### After — Employee not found:
```json
HTTP 404
Content-Type: application/json

{
  "timestamp": "2026-05-26T14:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Employee not found with ID: 99",
  "path": "/employees/99"
}
```

### After — Unexpected DB error:
```json
HTTP 500
Content-Type: application/json

{
  "timestamp": "2026-05-26T14:00:05Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred. Please try again later.",
  "path": "/leaves/apply"
}
```

---

## Implementation Order

1. Create `ErrorResponse.java` for each service
2. Create custom exception classes for each service
3. Create `GlobalExceptionHandler.java` for `employee-service`, `leave-management-service`, `authentication-service`
4. Create `GlobalErrorWebExceptionHandler.java` for `api-gateway`
5. Refactor `EmployeeController.java` — replace inline error returns with throws
6. Refactor `LeaveController.java` — replace inline error returns with throws (highest impact — 20+ changes)
7. Refactor `AuthController.java`
8. Test all error scenarios via Postman to verify consistent JSON shape
