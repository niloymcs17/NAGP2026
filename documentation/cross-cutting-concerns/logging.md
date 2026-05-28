# Logging – Implementation in Employee Leave Portal

This document explains how structured logging is implemented across all microservices in this project.

---

## Overview

All services previously used `System.out.println` for diagnostic output, which has no log levels, no timestamps, and cannot be filtered or integrated with log aggregation tools. These have been replaced with **SLF4J + Logback** — the standard Java logging stack.

No new Maven dependencies were required:
- **SLF4J** (`slf4j-api`) is already on the classpath via `spring-boot-starter`
- **Logback** is Spring Boot's default logging implementation — bundled automatically
- **`@Slf4j`** is a Lombok annotation — Lombok is already in the parent `pom.xml`

---

## How It Works

Every class that needs logging is annotated with `@Slf4j` (from Lombok). This generates a `log` field at compile time — equivalent to writing:

```java
private static final Logger log = LoggerFactory.getLogger(ClassName.class);
```

Log statements use **parameterized logging** (never string concatenation):

```java
// Correct — lazy evaluated, no string allocation if level is disabled
log.info("Employee created — id={}, username={}", id, username);

// Incorrect — always allocates a string even if INFO is disabled
log.info("Employee created — id=" + id + ", username=" + username);
```

---

## Log Levels Used

| Level | When It Is Used |
|-------|-----------------|
| `INFO` | Normal business events — request received, record created, event published/consumed |
| `WARN` | Expected but notable failures — validation errors, access denied, circuit breaker open |
| `DEBUG` | Verbose detail useful during development — JWT claims, record skipped |
| `ERROR` | Unexpected exceptions — event publish failures |

---

## Logging Configuration

The following `logging:` block was added to the `application.yml` of every service:

```yaml
# ── Logging ──────────────────────────────────────────────────────────────────
logging:
  level:
    root: INFO
    com.niloy: DEBUG       # full DEBUG for all project packages
    org.springframework: WARN  # suppress Spring framework noise
    org.hibernate: WARN        # suppress Hibernate SQL noise
```

This means:
- All project code (`com.niloy.*`) logs at DEBUG and above
- Spring and Hibernate framework internals are suppressed to WARN, keeping the console clean
- The global root level is INFO

**Files modified:**
- `api-gateway/src/main/resources/application.yml`
- `authentication-service/src/main/resources/application.yml`
- `employee-service/src/main/resources/application.yml`
- `leave-management-service/src/main/resources/application.yml`
- `notification-service/src/main/resources/application.yml`

---

## Log Events Per Service

### `api-gateway`

**File:** `api-gateway/.../filter/JwtAuthenticationFilter.java`

| Event | Level | Log Statement |
|-------|-------|---------------|
| Incoming request | DEBUG | `Incoming request: {} {}` (method + path) |
| JWT validation failed (no header / bad format / invalid token) | WARN | `JWT validation failed for path: {}` |
| JWT validated successfully | DEBUG | `JWT validated — userId={}, role={}, username={}` |

**File:** `api-gateway/.../controller/FallbackController.java`

| Event | Level | Log Statement |
|-------|-------|---------------|
| Auth Service circuit breaker open | WARN | `Circuit breaker OPEN — fallback triggered for Authentication Service` |
| Employee Service circuit breaker open | WARN | `Circuit breaker OPEN — fallback triggered for Employee Service` |
| Leave Service circuit breaker open | WARN | `Circuit breaker OPEN — fallback triggered for Leave Management Service` |

---

### `authentication-service`

**File:** `authentication-service/.../controller/AuthController.java`

| Event | Level | Log Statement |
|-------|-------|---------------|
| Login attempt received | INFO | `Login attempt for username: {}` |
| Login successful | INFO | `Login successful for username: {}` |
| Login failed (bad credentials) | WARN | `Login failed — invalid credentials for username: {}` |

**File:** `authentication-service/.../AuthenticationServiceApplication.java`

| Event | Level | Log Statement |
|-------|-------|---------------|
| Mock users seeded at startup | INFO | `Mock users seeded in Authentication Service.` |

---

### `employee-service`

**File:** `employee-service/.../controller/EmployeeController.java`

| Event | Level | Log Statement |
|-------|-------|---------------|
| Role check failed on create | WARN | `Access denied — role '{}' cannot create employees` |
| Duplicate employee ID on create | WARN | `Employee creation rejected — ID {} already exists` |
| Employee created successfully | INFO | `Employee created — id={}, username={}` |
| `employee.created` event published | INFO | `Published employee.created event for username: {}` |
| Employee retrieved | DEBUG | `Employee retrieved — id={}` |
| EMPLOYEE accessing another employee's data | WARN | `Access denied — userId={} attempted to access employee id={}` |
| MANAGER accessing outside their team | WARN | `Access denied — userId={} attempted to access employee id={}` |

**File:** `employee-service/.../EmployeeServiceApplication.java`

| Event | Level | Log Statement |
|-------|-------|---------------|
| Mock employees seeded at startup | INFO | `Mock employees seeded and events published.` |
| Event publish failure | ERROR | `Failed to publish employee.created event for username={}` (+ exception) |

---

### `leave-management-service`

**File:** `leave-management-service/.../controller/LeaveController.java`

| Event | Level | Log Statement |
|-------|-------|---------------|
| Leave application received | INFO | `Leave application received — employeeId={}, type={}, days={}` |
| Start date in the past | WARN | `Leave rejected — past start date — employeeId={}` |
| Insufficient leave balance | WARN | `Leave rejected — insufficient balance — employeeId={}, remaining={}, requested={}` |
| Overlapping dates detected | WARN | `Leave rejected — overlapping dates — employeeId={}` |
| Leave applied successfully | INFO | `Leave applied successfully — leaveId={}, employeeId={}` |
| Non-manager attempted approve/reject | WARN | `Forbidden — userId={} (role={}) attempted manager-only operation` |
| Leave request not found | WARN | `Leave request not found — id={}` |
| Leave approved | INFO | `Leave approved — leaveId={}, managerId={}` |
| Leave rejected | INFO | `Leave rejected — leaveId={}, managerId={}` |
| Leave cancelled | INFO | `Leave cancelled — leaveId={}, employeeId={}` |

**File:** `leave-management-service/.../consumer/EmployeeCreatedConsumer.java`

| Event | Level | Log Statement |
|-------|-------|---------------|
| `employee.created` event consumed | INFO | `Received employee.created event — employeeId={}, username={}` |
| Leave balances initialized | INFO | `Leave balances initialized — employeeId={}` |
| Leave balance already exists (skipped) | DEBUG | `Leave balances already exist for employeeId={} — skipping` |

**File:** `leave-management-service/.../LeaveManagementServiceApplication.java`

| Event | Level | Log Statement |
|-------|-------|---------------|
| Default balances seeded at startup | INFO | `Default leave balances seeded for pre-existing employees.` |

---

### `notification-service`

**File:** `notification-service/.../consumer/LeaveNotificationConsumer.java`

| Event | Level | Log Statement |
|-------|-------|---------------|
| `leave.notification` event consumed | INFO | `SIMULATED NOTIFICATION — type={}, employeeId={}, leaveId={}, status={}, dates={} to {}` |

> The original 15 `System.out.println` lines that printed a formatted block were collapsed into a single structured `log.info` statement carrying all key fields as named parameters. This makes the event machine-readable and compatible with log aggregation tools.

---

## Files Changed

| File | Change |
|------|--------|
| `api-gateway/.../JwtAuthenticationFilter.java` | `@Slf4j` + 3 log statements |
| `api-gateway/.../FallbackController.java` | `@Slf4j` + 3 `log.warn` (one per fallback) |
| `authentication-service/.../AuthController.java` | `@Slf4j` + 3 login flow logs |
| `authentication-service/.../AuthenticationServiceApplication.java` | `@Slf4j` + `println` → `log.info` |
| `employee-service/.../EmployeeController.java` | `@Slf4j` + `println` → `log.info` + 5 warn/debug statements |
| `employee-service/.../EmployeeServiceApplication.java` | `@Slf4j` + `println` → `log.info`, `System.err` → `log.error` |
| `leave-management-service/.../LeaveController.java` | `@Slf4j` + 10 log statements at all business events |
| `leave-management-service/.../EmployeeCreatedConsumer.java` | `@Slf4j` + 3 `println` → structured logs |
| `leave-management-service/.../LeaveManagementServiceApplication.java` | `@Slf4j` + `println` → `log.info` |
| `notification-service/.../LeaveNotificationConsumer.java` | `@Slf4j` + 15 `println` → 1 structured `log.info` |
| `api-gateway/src/main/resources/application.yml` | `logging:` config block added |
| `authentication-service/src/main/resources/application.yml` | `logging:` config block added |
| `employee-service/src/main/resources/application.yml` | `logging:` config block added |
| `leave-management-service/src/main/resources/application.yml` | `logging:` config block added |
| `notification-service/src/main/resources/application.yml` | `logging:` config block added |

**Total: 10 Java files + 5 YAML files = 15 file changes.**

---

## Sample Log Output

Below is what typical log output looks like after this implementation, when an employee applies for leave:

```
2026-05-28 10:22:01.412  DEBUG [api-gateway] --- Incoming request: POST /leaves/apply
2026-05-28 10:22:01.415  DEBUG [api-gateway] --- JWT validated — userId=1, role=EMPLOYEE, username=employee1
2026-05-28 10:22:01.431   INFO [leave-management-service] --- Leave application received — employeeId=1, type=CASUAL, days=3
2026-05-28 10:22:01.445   INFO [leave-management-service] --- Leave applied successfully — leaveId=12, employeeId=1
2026-05-28 10:22:01.460   INFO [notification-service] --- SIMULATED NOTIFICATION — type=APPLICATION, employeeId=1, leaveId=12, status=PENDING, dates=2026-06-02 to 2026-06-04
```

And when a validation fails:

```
2026-05-28 10:23:15.302   INFO [leave-management-service] --- Leave application received — employeeId=2, type=SICK, days=12
2026-05-28 10:23:15.318   WARN [leave-management-service] --- Leave rejected — insufficient balance — employeeId=2, remaining=8, requested=12
```

---

## How to Access and Configure Logs

Depending on how the services are run, logs can be accessed and configured in the following ways:

### 1. Direct Console Output (Running via Maven/Java)
When running services locally using the Spring Boot Maven plugin or as direct JAR files, logs stream directly to the terminal stdout:
```bash
# Run service directly in console
mvn spring-boot:run
```
Or running the compiled JAR:
```bash
java -jar target/leave-management-service-0.0.1-SNAPSHOT.jar
```

### 2. Running via Docker Compose
When running the application stack via [docker-compose.yml](file:///g:/NAGP2026/docker-compose.yml):
* **Tail logs for all services:**
  ```bash
  docker-compose logs -f
  ```
* **Tail logs for a specific service:**
  ```bash
  docker-compose logs -f leave-management-service
  ```
* **Filter logs in real time:**
  ```bash
  docker-compose logs -f employee-service | grep "com.niloy"
  ```

### 3. Persisting Logs to a File
By default, logs are only output to the console. To write logs to a file, configure the `logging.file.name` property in the service's `application.yml`:
```yaml
logging:
  file:
    name: logs/leave-management-service.log
```
This will automatically generate a rolling log file under a `logs/` directory relative to the directory from which the application is run.

### 4. Distributed Tracing Correlation
Since OpenTelemetry and Jaeger are integrated in this workspace (see [distributed-tracing.md](file:///g:/NAGP2026/documentation/cross-cutting-concerns/distributed_tracing.md)), every log statement is automatically decorated with the current `traceId` and `spanId` when a distributed tracing context is active. This allows you to track a request flow across all microservices.
