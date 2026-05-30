# Authentication & Authorization – Employee Leave Portal

This document explains how authentication (verifying *who* a user is) and authorization (verifying *what* a user can do) are implemented as cross-cutting concerns across all microservices in this project.

---

## Overview

Security is enforced at **two layers**:

| Layer | Service | Responsibility |
|-------|---------|----------------|
| **Authentication** | `authentication-service` | Validate credentials, issue signed JWT tokens |
| **Authorization (Gateway)** | `api-gateway` | Intercept every inbound request, validate JWT, propagate identity headers |
| **Authorization (Business)** | `employee-service`, `leave-management-service` | Read propagated headers and enforce role-based rules |

The design ensures that **no downstream microservice trusts the raw client request**. The gateway acts as a single enforcement point: if a token is absent or invalid, the request is rejected before it ever reaches a business service.

---

## Architecture

```
Client (Browser / API Client)
        │
        │  POST /auth/login  {username, password}
        ▼
┌─────────────────────────────────────────────────────────┐
│                     API Gateway (:8080)                  │
│                                                          │
│  Route: /auth/**  → NO JWT filter applied               │
│  Route: /employees/** → JwtAuthenticationFilter ✓       │
│  Route: /leaves/**    → JwtAuthenticationFilter ✓       │
└────────────┬────────────────────┬───────────────────────┘
             │                    │
             ▼                    ▼
  ┌─────────────────┐   ┌─────────────────────────┐
  │ authentication- │   │   employee-service /     │
  │ service (:8081) │   │ leave-management-service │
  │                 │   │                          │
  │  Issues JWT     │   │  Reads X-User-Id,        │
  │  token on login │   │  X-User-Role,            │
  └─────────────────┘   │  X-User-Username         │
                        │  from request headers    │
                        └─────────────────────────┘
```

---

## Part 1 – Authentication (`authentication-service`)

### Responsibility

The Authentication Service is the only component that has access to the user credentials database. Its sole job is to validate a username/password pair and return a signed JWT if the credentials are correct.

### User Model

**File:** [`User.java`](/authentication-service/src/main/java/com/niloy/auth/model/User.java)

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;          // BCrypt-hashed, never stored in plaintext

    @Column(nullable = false)
    private String role;              // "EMPLOYEE" or "MANAGER"
    ...
}
```

Passwords are **never stored in plaintext**. All passwords are hashed with BCrypt before persistence (see [Security Configuration](#security-configuration) below).

### Seeded Test Users

At startup, the service seeds three in-memory users if the `users` table is empty:

**File:** [`AuthenticationServiceApplication.java`](/authentication-service/src/main/java/com/niloy/auth/AuthenticationServiceApplication.java)

| Username | Password | Role |
|----------|----------|------|
| `employee1` | `password` | `EMPLOYEE` |
| `employee2` | `password` | `EMPLOYEE` |
| `manager1` | `password` | `MANAGER` |

### Login Endpoint

**File:** [`AuthController.java`](/authentication-service/src/main/java/com/niloy/auth/controller/AuthController.java)

```
POST /auth/login
```

**Request body:**
```json
{
  "username": "employee1",
  "password": "password"
}
```

**Successful response (`200 OK`):**
```json
{
  "token": "<JWT>",
  "userId": 1,
  "username": "employee1",
  "role": "EMPLOYEE"
}
```

**Failed response (`401 Unauthorized`):**
```json
{
  "error": "Invalid username or password"
}
```

**Flow inside `AuthController.login()`:**

1. Look up the user by username from the PostgreSQL `users` table.
2. Use `BCryptPasswordEncoder.matches()` to compare the submitted password against the stored hash.
3. If either step fails → throw `InvalidCredentialsException` → `GlobalExceptionHandler` returns `401`.
4. If credentials are valid → call `JwtUtil.generateToken()` → return `AuthResponse`.

### JWT Token Generation

**File:** [`JwtUtil.java` (auth-service)](/authentication-service/src/main/java/com/niloy/auth/util/JwtUtil.java)

```java
public String generateToken(Long userId, String username, String role) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", role);
    claims.put("username", username);

    return Jwts.builder()
            .setClaims(claims)
            .setSubject(userId.toString())    // subject = userId
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
}
```

| JWT Field | Value |
|-----------|-------|
| **Algorithm** | `HS256` (HMAC-SHA256) |
| **Subject (`sub`)** | User ID (as string) |
| **Custom claim: `role`** | `EMPLOYEE` or `MANAGER` |
| **Custom claim: `username`** | Authenticated username |
| **Expiry** | 24 hours (`86400000` ms) |
| **Signing key** | Shared HMAC secret (see [Configuration](#configuration)) |

### Security Configuration

**File:** [`SecurityConfig.java`](/authentication-service/src/main/java/com/niloy/auth/config/SecurityConfig.java)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

> **Why `permitAll()`?** The Authentication Service's own HTTP layer is open because its single endpoint (`/auth/login`) must be publicly accessible. The Spring Security `PasswordEncoder` bean is still essential — it is injected into `AuthController` to perform BCrypt comparison.

---

## Part 2 – Gateway-Level JWT Validation (`api-gateway`)

### Responsibility

The API Gateway intercepts **every inbound HTTP request**. For all routes except `/auth/**`, it validates the JWT from the `Authorization` header before forwarding to the downstream service.

### Route Configuration

**File:** [`application.yml`](/api-gateway/src/main/resources/application.yml)

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Auth route — NO JWT filter (public endpoint)
        - id: authentication-service
          uri: lb://AUTHENTICATION-SERVICE
          predicates:
            - Path=/auth/**
          filters:
            - name: CircuitBreaker
              args: { name: authServiceCB, fallbackUri: forward:/fallback/auth }

        # Employee routes — JWT filter applied
        - id: employee-service
          uri: lb://EMPLOYEE-SERVICE
          predicates:
            - Path=/employees/**
          filters:
            - name: JwtAuthenticationFilter
            - name: CircuitBreaker
              args: { name: employeeServiceCB, fallbackUri: forward:/fallback/employee }

        # Leave routes — JWT filter applied
        - id: leave-management-service
          uri: lb://LEAVE-MANAGEMENT-SERVICE
          predicates:
            - Path=/leaves/**
          filters:
            - name: JwtAuthenticationFilter
            - name: CircuitBreaker
              args: { name: leaveServiceCB, fallbackUri: forward:/fallback/leave }
```

The `JwtAuthenticationFilter` is applied **only** to routes that require authentication. The `/auth/**` path is intentionally excluded.

### JWT Validation Filter

**File:** [`JwtAuthenticationFilter.java`](/api-gateway/src/main/java/com/niloy/gateway/filter/JwtAuthenticationFilter.java)

The filter executes the following steps for every request on a protected route:

```
Request arrives at gateway
        │
        ├─ Path contains "/auth/login"? ──YES──► Forward without validation
        │
        ├─ Authorization header missing? ──YES──► 401 Unauthorized
        │
        ├─ Header not "Bearer <token>"? ──YES──► 401 Unauthorized
        │
        ├─ Token signature invalid / expired? ──YES──► 401 Unauthorized
        │
        └─ Token valid ──────────────────────────► Extract claims
                                                   Mutate request headers
                                                   Forward to downstream service
```

**Claims extraction and header injection:**

```java
Claims claims = jwtUtil.getClaims(token);

String userId   = claims.getSubject();                    // → X-User-Id
String role     = claims.get("role", String.class);       // → X-User-Role
String username = claims.get("username", String.class);   // → X-User-Username

ServerHttpRequest mutatedRequest = request.mutate()
        .header("X-User-Id", userId)
        .header("X-User-Role", role)
        .header("X-User-Username", username)
        .build();
```

After the gateway injects these headers, **no downstream service needs to parse or validate the JWT**. They simply read trusted headers.

### Gateway JWT Utility

**File:** [`JwtUtil.java` (api-gateway)](/api-gateway/src/main/java/com/niloy/gateway/util/JwtUtil.java)

The gateway has its own `JwtUtil` (read-only, no token generation). It provides two operations:

| Method | Purpose |
|--------|---------|
| `validateToken(String token)` | Returns `true` if token is valid (correct signature + not expired) |
| `getClaims(String token)` | Parses and returns the full `Claims` object |

```java
public boolean validateToken(String token) {
    try {
        Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token);
        return !isTokenExpired(token);
    } catch (Exception e) {
        return false;    // any parse/signature error → invalid
    }
}
```

The same HMAC secret (`jwt.secret`) must be present in **both** `authentication-service` and `api-gateway`. Token generation and validation use the same key — this is the shared-secret model.

---

## Part 3 – Role-Based Authorization (Downstream Services)

Once the gateway has validated the JWT and injected `X-User-Id`, `X-User-Role`, and `X-User-Username` headers, individual business services perform **role-based access control (RBAC)** by reading those headers directly.

### Roles

| Role | Description |
|------|-------------|
| `EMPLOYEE` | Standard user — can apply, view, and cancel their own leave requests |
| `MANAGER` | Privileged user — can approve/reject leave requests and view all employees |

### Example: Leave Management Service

The `LeaveController` reads the injected headers to decide whether an operation is permitted:

```java
// Example: Only MANAGERs can approve or reject leave requests
@PatchMapping("/{id}/approve")
public ResponseEntity<?> approveLeave(
        @PathVariable Long id,
        @RequestHeader("X-User-Role") String role,
        @RequestHeader("X-User-Id") String userId) {

    if (!"MANAGER".equals(role)) {
        log.warn("Forbidden — userId={} (role={}) attempted manager-only operation", userId, role);
        throw new ForbiddenException("Only managers can approve leave requests");
    }
    // ... approve logic
}
```

### Example: Employee Service

The `EmployeeController` enforces ownership checks using the `X-User-Id` header:

```java
// An EMPLOYEE can only view their own record
@GetMapping("/{id}")
public ResponseEntity<?> getEmployee(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") String userId,
        @RequestHeader("X-User-Role") String role) {

    if ("EMPLOYEE".equals(role) && !userId.equals(id.toString())) {
        log.warn("Access denied — userId={} attempted to access employee id={}", userId, id);
        throw new ForbiddenException("Access denied");
    }
    // ... fetch logic
}
```

### Authorization Matrix

| Operation | `EMPLOYEE` | `MANAGER` |
|-----------|:----------:|:---------:|
| `POST /auth/login` | ✅ | ✅ |
| `GET /employees/{own-id}` | ✅ | ✅ |
| `GET /employees/{other-id}` | ❌ | ✅ |
| `POST /employees` | ❌ | ✅ |
| `POST /leaves/apply` | ✅ | ❌ |
| `GET /leaves/{own-id}` | ✅ | ✅ |
| `PATCH /leaves/{id}/approve` | ❌ | ✅ |
| `PATCH /leaves/{id}/reject` | ❌ | ✅ |
| `DELETE /leaves/{id}/cancel` | ✅ | ❌ |

---

## Configuration

Both `authentication-service` and `api-gateway` must share the same `jwt.secret`. The values must match exactly.

### `authentication-service/src/main/resources/application.yml`

```yaml
server:
  port: 8081

jwt:
  secret: 3cO0m6pP9qRsTuVwXyZ1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t1u2v3
  expiration-ms: 86400000    # 24 hours in milliseconds
```

### `api-gateway/src/main/resources/application.yml`

```yaml
server:
  port: 8080

jwt:
  secret: 3cO0m6pP9qRsTuVwXyZ1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0t1u2v3
  # No expiration-ms needed — the gateway only validates, never generates tokens
```

> **Important:** In a production environment, the `jwt.secret` should be injected via an environment variable (`JWT_SECRET`) and **never** committed to version control. The value above is a development-time default only.

---

## Dependencies

The JWT library used is **JJWT** (`io.jsonwebtoken`). Both services declare the same three artifacts:

```xml
<!-- authentication-service/pom.xml and api-gateway/pom.xml -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
</dependency>
```

Version management is handled by the parent POM (`leave-portal-parent`).

The `authentication-service` additionally depends on:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

This provides `BCryptPasswordEncoder` and the `SecurityFilterChain` infrastructure. **The `api-gateway` does not have this dependency** — it does not need Spring Security since JWT validation is done manually in the reactive `GatewayFilterFactory`.

---

## Request Lifecycle

The following shows the complete flow for an authenticated API call (e.g., an employee applying for leave):

```
1. Client → POST /auth/login { username, password }
   └── Gateway: /auth/** route has no JWT filter → forwarded directly

2. authentication-service:
   ├── Looks up user by username
   ├── BCrypt.matches(plaintext, hashedPassword) → true
   └── Returns { token: "eyJ...", userId: 1, username: "employee1", role: "EMPLOYEE" }

3. Client → POST /leaves/apply
   Headers: { Authorization: "Bearer eyJ..." }
   └── Gateway: /leaves/** → JwtAuthenticationFilter runs

4. JwtAuthenticationFilter:
   ├── Extracts "Bearer eyJ..." → strips prefix → raw token
   ├── jwtUtil.validateToken(token) → true
   ├── jwtUtil.getClaims(token) → { sub: "1", role: "EMPLOYEE", username: "employee1" }
   └── Mutates request:
       ├── X-User-Id: "1"
       ├── X-User-Role: "EMPLOYEE"
       └── X-User-Username: "employee1"

5. leave-management-service:
   ├── Reads X-User-Role → "EMPLOYEE" → permitted to apply
   ├── Reads X-User-Id  → "1"        → sets employeeId = 1
   └── Processes leave application
```

---

## Error Responses

| Scenario | HTTP Status | Message |
|----------|:-----------:|---------|
| Missing `Authorization` header | `401` | `No Authorization Header` |
| Header present but not `Bearer ...` format | `401` | `Invalid Authorization Header Format` |
| Token signature invalid or tampered | `401` | `Invalid Token` |
| Token expired | `401` | `Invalid Token` |
| Wrong username or password on login | `401` | `Invalid username or password` |
| `EMPLOYEE` attempts manager-only action | `403` | `Only managers can...` |
| `EMPLOYEE` attempts to access another employee's data | `403` | `Access denied` |
| `MANAGER` attempts to create own leave | `403` | *(role-checked in business layer)* |

---

## Key Design Decisions

### 1. Stateless JWT — No Server-Side Session

The system uses **stateless JWT** tokens. No session state is stored on the server. Each request is self-contained — the token carries all the identity and role information the services need. This is natural for a horizontally scalable microservices architecture.

### 2. Single Enforcement Point at the Gateway

JWT validation happens **once** at the gateway, not in every downstream service. Downstream services trust the `X-User-*` headers because they can only be set by the gateway — clients have no direct access to downstream services.

### 3. Shared Secret (HS256) vs. Public Key (RS256)

The current implementation uses a **shared HMAC secret** (HS256). This means both the authentication service (which signs) and the gateway (which verifies) must have the same secret. A production system would typically use **RS256** with a private key for signing and a public key for verification, eliminating the need to share a secret. The HS256 approach is sufficient for this assignment scope.

### 4. No JWT Parsing in Business Services

Business services (`employee-service`, `leave-management-service`) **never parse the JWT**. They only read the `X-User-Id`, `X-User-Role`, and `X-User-Username` headers. This eliminates JJWT as a dependency from those services and keeps the authorization logic simple.

### 5. BCrypt for Password Hashing

Passwords are hashed using **BCrypt** with Spring Security's `BCryptPasswordEncoder`. BCrypt is adaptive (cost factor can be increased as hardware improves) and automatically incorporates a random salt, making rainbow table attacks infeasible.

---

## Related Documents

- [Circuit Breaker Pattern](/documentation/cross-cutting-concerns/circuit_breaker_pattern.md) — The `authServiceCB` circuit breaker wraps the `/auth/**` route with a stricter configuration (`slidingWindowSize: 10`, `waitDurationInOpenState: 10s`)
- [Logging](/documentation/cross-cutting-concerns/logging.md) — Login attempts, JWT validation successes/failures, and access-denied events are all logged at appropriate levels
- [Distributed Tracing](/documentation/cross-cutting-concerns/distributed_tracing.md) — OpenTelemetry trace context is propagated across all service calls, including authentication
- [Global Exception Handling](/documentation/cross-cutting-concerns/global_exception_handling.md) — `InvalidCredentialsException` is mapped to `401` via the global handler in the authentication service

---

## Files Reference

| File | Role |
|------|------|
| [`authentication-service/.../AuthController.java`](/authentication-service/src/main/java/com/niloy/auth/controller/AuthController.java) | Login endpoint — credential validation + token issuance |
| [`authentication-service/.../JwtUtil.java`](/authentication-service/src/main/java/com/niloy/auth/util/JwtUtil.java) | JWT token generation (sign + encode claims) |
| [`authentication-service/.../SecurityConfig.java`](/authentication-service/src/main/java/com/niloy/auth/config/SecurityConfig.java) | BCryptPasswordEncoder bean + open security chain |
| [`authentication-service/.../User.java`](/authentication-service/src/main/java/com/niloy/auth/model/User.java) | JPA entity — persisted users with hashed passwords and roles |
| [`authentication-service/.../InvalidCredentialsException.java`](/authentication-service/src/main/java/com/niloy/auth/exception/InvalidCredentialsException.java) | Custom exception mapped to `401` |
| [`api-gateway/.../JwtAuthenticationFilter.java`](/api-gateway/src/main/java/com/niloy/gateway/filter/JwtAuthenticationFilter.java) | Reactive gateway filter — validates JWT, injects identity headers |
| [`api-gateway/.../JwtUtil.java`](/api-gateway/src/main/java/com/niloy/gateway/util/JwtUtil.java) | JWT parsing and validation (read-only, no token generation) |
| [`api-gateway/.../application.yml`](/api-gateway/src/main/resources/application.yml) | Route definitions — `JwtAuthenticationFilter` applied to `/employees/**` and `/leaves/**` |
| [`authentication-service/.../application.yml`](/authentication-service/src/main/resources/application.yml) | `jwt.secret` and `jwt.expiration-ms` configuration |
