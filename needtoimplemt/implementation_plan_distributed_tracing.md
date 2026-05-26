# Distributed Tracing – Fix & Complete Implementation Plan
## Employee Leave Management Portal

---

## Current State

The tracing stack is **partially wired** — dependencies exist and Jaeger runs in Docker, but the configuration is incomplete, making tracing unreliable.

### What Already Works ✅
| Component | Detail |
|-----------|--------|
| **Jaeger** container | Running in `docker-compose.yml` on port `16686` (UI) and `4318` (OTLP HTTP receiver) |
| **`micrometer-tracing-bridge-otel`** | In `pom.xml` of 5 services (api-gateway, auth, employee, leave, notification) |
| **`opentelemetry-exporter-otlp`** | In `pom.xml` of 5 services |
| **`spring.application.name`** | Set correctly in all services — used as the service name label in Jaeger |
| **Docker env var** | `MANAGEMENT_OTLP_TRACING_ENDPOINT=http://jaeger:4318/v1/traces` injected by Docker Compose |

### What Is Missing / Broken ❌

| Gap | Affected Services | Impact |
|-----|-------------------|--------|
| `management.otlp.tracing.endpoint` not in `application.yml` | All 5 services | Tracing only works in Docker; **broken in local dev** |
| `management.tracing.sampling.probability` not configured | All 5 services | Defaults to `0.1` → **90% of traces silently dropped** |
| `management` block missing entirely in 4 services | auth, employee, leave, notification | No actuator or tracing config at all |

---

## How Tracing Works in This Project

```
Client Request
      │
      ▼
API Gateway ──── generates traceId + spanId ──► Jaeger :4318
      │                                               │
      ▼                                               │
Authentication / Employee / Leave Service             │
      │ ── propagates same traceId ──────────────────►│
      │                                               │
      ▼                                               │
RabbitMQ message published                            │
      │                                               │
      ▼                                               │
Notification / Leave Consumer                         │
      │ ── same traceId in message headers ──────────►│
                                                      ▼
                                              Jaeger UI :16686
                                        (search by traceId, see full journey)
```

Every hop in the request chain gets the **same `traceId`** and its own `spanId`. You can open Jaeger UI and see the full tree of spans for a single user request.

---

## Files to Modify

Only **5 `application.yml` files** need to be changed. No Java code, no `pom.xml`, no Docker changes.

---

### 1. `api-gateway/src/main/resources/application.yml` — [MODIFY]

The `management:` block already exists (added for Circuit Breaker actuator). **Add tracing config inside it:**

```yaml
# Existing management block — ADD the two tracing keys below:
management:
  endpoints:
    web:
      exposure:
        include: health,info,circuitbreakers,circuitbreakerevents
  endpoint:
    health:
      show-details: always
  health:
    circuitbreakers:
      enabled: true
  # ── ADD THESE ──────────────────────────────────────────────────
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces   # local dev; Docker overrides via env var
  tracing:
    sampling:
      probability: 1.0                            # trace 100% of requests
```

---

### 2. `authentication-service/src/main/resources/application.yml` — [MODIFY]

No `management` block currently exists. **Add it at the bottom:**

```yaml
# ── Distributed Tracing ─────────────────────────────────────────────────────
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
  tracing:
    sampling:
      probability: 1.0
```

---

### 3. `employee-service/src/main/resources/application.yml` — [MODIFY]

No `management` block currently exists. **Add it at the bottom:**

```yaml
# ── Distributed Tracing ─────────────────────────────────────────────────────
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
  tracing:
    sampling:
      probability: 1.0
```

---

### 4. `leave-management-service/src/main/resources/application.yml` — [MODIFY]

No `management` block currently exists. **Add it at the bottom:**

```yaml
# ── Distributed Tracing ─────────────────────────────────────────────────────
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
  tracing:
    sampling:
      probability: 1.0
```

---

### 5. `notification-service/src/main/resources/application.yml` — [MODIFY]

No `management` block currently exists. **Add it at the bottom:**

```yaml
# ── Distributed Tracing ─────────────────────────────────────────────────────
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
  tracing:
    sampling:
      probability: 1.0
```

---

## Why Docker Compose Does NOT Need Changing

The `docker-compose.yml` already injects:
```yaml
- MANAGEMENT_OTLP_TRACING_ENDPOINT=http://jaeger:4318/v1/traces
```
for all 5 services. Spring Boot environment variables **override** `application.yml` values, so in Docker the correct internal hostname (`jaeger`) is automatically used instead of `localhost`.

| Environment | Endpoint Used | Source |
|-------------|--------------|--------|
| Local dev (`mvn spring-boot:run`) | `http://localhost:4318/v1/traces` | `application.yml` (being added) |
| Docker Compose | `http://jaeger:4318/v1/traces` | Docker env var (already present) |

---

## Sampling Probability Guidance

| Value | Meaning | When to Use |
|-------|---------|-------------|
| `1.0` | 100% — every request traced | ✅ Development / testing |
| `0.1` | 10% — 1 in 10 requests traced | Production (high traffic) |
| `0.5` | 50% | Staging / medium traffic |

Set `1.0` now so every Postman request appears in Jaeger during development.

---

## How to Verify After Implementation

1. Start all services (`docker-compose up` or locally with Jaeger running)
2. Send any API request via Postman — e.g., `POST /auth/login`
3. Open Jaeger UI: `http://localhost:16686`
4. Select service: `api-gateway` from the dropdown
5. Click **Find Traces** → you should see the trace with spans across services

### What a Full Leave Application Trace Looks Like in Jaeger
```
traceId: abc123
│
├── api-gateway                  [span: 2ms]  ← entry point
│     └── leave-management-service [span: 15ms] ← HTTP call
│           └── leave-management-service (DB) [span: 3ms]
│           └── leave-management-service (RabbitMQ publish) [span: 1ms]
│                 └── notification-service (RabbitMQ consume) [span: 2ms]
```

---

## Files Summary

| File | Action | Lines to Add |
|------|--------|-------------|
| `api-gateway/src/main/resources/application.yml` | MODIFY | 4 lines inside existing `management:` block |
| `authentication-service/src/main/resources/application.yml` | MODIFY | 6 lines at bottom |
| `employee-service/src/main/resources/application.yml` | MODIFY | 6 lines at bottom |
| `leave-management-service/src/main/resources/application.yml` | MODIFY | 6 lines at bottom |
| `notification-service/src/main/resources/application.yml` | MODIFY | 6 lines at bottom |

**Total: 5 YAML files — no Java, no pom.xml, no Docker changes.**

---

## Implementation Order

1. Modify `authentication-service/application.yml`
2. Modify `employee-service/application.yml`
3. Modify `leave-management-service/application.yml`
4. Modify `notification-service/application.yml`
5. Modify `api-gateway/application.yml` (merge into existing `management:` block carefully)
6. Restart services and verify in Jaeger UI at `http://localhost:16686`
