# Global Exception Handling 

This document explains how **Global Exception Handling** is implemented in this project, why it was needed, and exactly how every error flows from controller to client.

---

## Standard Error Response Shape

After implementation, **every error** from every service returns this consistent JSON shape:

```json
{
  "timestamp": "2026-05-26T14:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Leave request not found with ID: 99",
  "path": "/leaves/99/approve"
}
```

This is produced by the `ErrorResponse` Java record, shared across all services.

---

## Services in Scope

| Service | Handler Type |
|---------|-------------|
| `leave-management-service` | Spring MVC `@RestControllerAdvice` |
| `employee-service` | Spring MVC `@RestControllerAdvice` |
| `authentication-service` | Spring MVC `@RestControllerAdvice` |
| `api-gateway` | Reactive WebFlux `AbstractErrorWebExceptionHandler` |
| `notification-service` | No REST controller — consumer only |
| `eureka-server` | No business REST controller |

---

## Architecture: How an Error Flows

```
Client Request
      │
      ▼
  Controller Method
      │
      │  throws CustomException (e.g., LeaveRequestNotFoundException)
      │
      ▼
  GlobalExceptionHandler  (@RestControllerAdvice)
      │
      │  @ExceptionHandler(LeaveRequestNotFoundException.class)
      │  builds ErrorResponse.of(404, "Not Found", ex.getMessage(), path)
      │
      ▼
  HTTP Response
  {
    "timestamp": "...",
    "status": 404,
    "error": "Not Found",
    "message": "Leave request not found with ID: 99",
    "path": "/leaves/99/approve"
  }
```

For the **API Gateway** (reactive), `GlobalErrorWebExceptionHandler` extends `AbstractErrorWebExceptionHandler` and intercepts all unhandled errors before they reach the client.

---

## Implementation Files

### 1 – ErrorResponse (Shared Model)

Each service has its own `ErrorResponse` record inside its `exception` package.

**Package:** `com.niloy.<module>.exception.ErrorResponse`

```java
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

| File | Service |
|------|---------|
| `employee-service/.../exception/ErrorResponse.java` | `com.niloy.employee.exception` |
| `leave-management-service/.../exception/ErrorResponse.java` | `com.niloy.leave.exception` |
| `authentication-service/.../exception/ErrorResponse.java` | `com.niloy.auth.exception` |

---

### 2 – Custom Domain Exceptions

Controllers throw typed exceptions rather than building error responses inline. Each exception encodes the business meaning of the failure.

#### `employee-service`

| Exception | HTTP Status | Message |
|-----------|------------|---------|
| `EmployeeNotFoundException(Long id)` | 404 | `Employee not found with ID: <id>` |
| `EmployeeAlreadyExistsException(Long id)` | 400 | `Employee already exists with ID: <id>` |
| `AccessDeniedException(String message)` | 403 | Custom role-based message |

#### `leave-management-service`

| Exception | HTTP Status | Message |
|-----------|------------|---------|
| `LeaveRequestNotFoundException(Long id)` | 404 | `Leave request not found with ID: <id>` |
| `InsufficientLeaveBalanceException(int remaining, int requested)` | 400 | `Insufficient leave balance. Remaining: X, Requested: Y` |
| `InvalidLeaveRequestException(String message)` | 400 | Custom business rule message |
| `LeaveConflictException()` | 409 | `Overlapping leave request detected for these dates.` |
| `AccessDeniedException(String message)` | 403 | Custom role-based message |

#### `authentication-service`

| Exception | HTTP Status | Message |
|-----------|------------|---------|
| `InvalidCredentialsException()` | 401 | `Invalid username or password` |

---

### 3 – GlobalExceptionHandler (MVC Services)

Each of the three Spring MVC services has a `GlobalExceptionHandler` annotated with `@RestControllerAdvice`. Spring automatically routes any thrown exception from any controller in that service to the matching `@ExceptionHandler` method.

**File:** `<service>/src/main/java/com/niloy/<module>/exception/GlobalExceptionHandler.java`

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EmployeeNotFoundException ex, HttpServletRequest req) {
        log.warn("Resource not found at [{}]: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not Found", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied at [{}]: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "Forbidden", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed at [{}]: {}", req.getRequestURI(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Validation Failed", message, req.getRequestURI()));
    }

    // Catch-all safety net
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at [{}]: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred. Please try again later.", req.getRequestURI()));
    }
}
```

**Handler coverage per service:**

| Handler Method | Covers | Status |
|---------------|--------|--------|
| `handleNotFound` | `EmployeeNotFoundException`, `LeaveRequestNotFoundException` | 404 |
| `handleBadRequest` | `EmployeeAlreadyExistsException`, `InsufficientLeaveBalanceException`, `InvalidLeaveRequestException` | 400 |
| `handleConflict` *(leave only)* | `LeaveConflictException` | 409 |
| `handleForbidden` | `AccessDeniedException` | 403 |
| `handleUnauthorized` *(auth only)* | `InvalidCredentialsException` | 401 |
| `handleValidation` | `MethodArgumentNotValidException` (from `@Valid`) | 400 |
| `handleGeneric` | Any uncaught `Exception` | 500 |

---

### 4 – GlobalErrorWebExceptionHandler (API Gateway)

The API Gateway uses Spring WebFlux (reactive stack), so `@RestControllerAdvice` does not apply. Instead, a component extending `AbstractErrorWebExceptionHandler` intercepts all errors.

**File:** `api-gateway/src/main/java/com/niloy/gateway/exception/GlobalErrorWebExceptionHandler.java`

```java
@Component
@Order(-2) // higher priority than Spring's DefaultErrorWebExceptionHandler
public class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

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

**Why `@Order(-2)`?**
Spring Boot registers a `DefaultErrorWebExceptionHandler` at order `-1`. By using `-2`, our custom handler takes priority and overrides the default HTML error page with our JSON shape.

---

## Error Response Examples

### 404 – Employee not found

**Before:**
```
HTTP 404
Content-Type: text/plain

Employee not found
```

**After:**
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

---

### 400 – Insufficient leave balance

```json
HTTP 400
Content-Type: application/json

{
  "timestamp": "2026-05-26T14:01:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient leave balance. Remaining: 2, Requested: 5",
  "path": "/leaves/apply"
}
```

---

### 403 – Role-based access denied

```json
HTTP 403
Content-Type: application/json

{
  "timestamp": "2026-05-26T14:02:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Only managers can approve leave requests",
  "path": "/leaves/12/approve"
}
```

---

### 409 – Overlapping leave dates

```json
HTTP 409
Content-Type: application/json

{
  "timestamp": "2026-05-26T14:03:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Overlapping leave request detected for these dates.",
  "path": "/leaves/apply"
}
```

---

### 401 – Invalid credentials

```json
HTTP 401
Content-Type: application/json

{
  "timestamp": "2026-05-26T14:04:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password",
  "path": "/auth/login"
}
```

---

### 500 – Unexpected error (catch-all)

```json
HTTP 500
Content-Type: application/json

{
  "timestamp": "2026-05-26T14:05:00Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred. Please try again later.",
  "path": "/leaves/apply"
}
```



