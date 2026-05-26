# Health Check Endpoints – Implementation Plan
## Employee Leave Management Portal

---

## Current Status

Spring Boot Actuator provides `/actuator/health` out of the box. It currently exists **only on the API Gateway** — the other 5 services have neither the dependency nor any configuration.

| Service | Port | Actuator Dependency | Health Endpoint |
|---------|------|---------------------|-----------------|
| `api-gateway` | 8080 | ✅ Already present | ✅ `/actuator/health` works |
| `authentication-service` | 8081 | ❌ Missing | ❌ Not available |
| `employee-service` | 8082 | ❌ Missing | ❌ Not available |
| `leave-management-service` | 8083 | ❌ Missing | ❌ Not available |
| `notification-service` | 8084 | ❌ Missing | ❌ Not available |
| `eureka-server` | 8761 | ❌ Missing | ❌ Not available |

---

## What the Health Endpoint Shows

Spring Boot Actuator auto-detects components and reports their health. For your services:

| Service | Health Indicators Reported |
|---------|---------------------------|
| `authentication-service` | `db` (H2), `diskSpace`, `ping` |
| `employee-service` | `db` (H2), `rabbit` (RabbitMQ), `diskSpace`, `ping` |
| `leave-management-service` | `db` (H2), `rabbit` (RabbitMQ), `diskSpace`, `ping` |
| `notification-service` | `rabbit` (RabbitMQ), `diskSpace`, `ping` |
| `eureka-server` | `diskSpace`, `ping` |

---

## Files to Modify

### 1. `authentication-service/pom.xml` — [MODIFY]
Add the actuator dependency inside `<dependencies>`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 2. `employee-service/pom.xml` — [MODIFY]
Same dependency as above.

### 3. `leave-management-service/pom.xml` — [MODIFY]
Same dependency as above.

### 4. `notification-service/pom.xml` — [MODIFY]
Same dependency as above.

### 5. `eureka-server/pom.xml` — [MODIFY]
Same dependency as above.

---

### 6. `authentication-service/src/main/resources/application.yml` — [MODIFY]
Add at the bottom:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

### 7. `employee-service/src/main/resources/application.yml` — [MODIFY]
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

### 8. `leave-management-service/src/main/resources/application.yml` — [MODIFY]
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

### 9. `notification-service/src/main/resources/application.yml` — [MODIFY]
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

### 10. `eureka-server/src/main/resources/application.yml` — [MODIFY]
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

---

## Health Endpoint URLs After Implementation

| Service | Health Check URL |
|---------|-----------------|
| API Gateway | `GET http://localhost:8080/actuator/health` ✅ already works |
| Authentication Service | `GET http://localhost:8081/actuator/health` |
| Employee Service | `GET http://localhost:8082/actuator/health` |
| Leave Management Service | `GET http://localhost:8083/actuator/health` |
| Notification Service | `GET http://localhost:8084/actuator/health` |
| Eureka Server | `GET http://localhost:8761/actuator/health` |

---

## Sample Health Response

A healthy `employee-service` will respond with:

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "H2",
        "validationQuery": "isValid()"
      }
    },
    "rabbit": {
      "status": "UP",
      "details": {
        "version": "3.12.x"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 430000000000,
        "threshold": 10485760
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

If RabbitMQ is down, the `rabbit` component shows `"status": "DOWN"` and the overall `status` becomes `"DOWN"`.

---

## Implementation Order

1. Add `spring-boot-starter-actuator` to each of the 5 `pom.xml` files
2. Add `management:` config block to each of the 5 `application.yml` files
3. Rebuild the services (`mvn clean package` or Docker rebuild)
4. Verify each endpoint with `GET /actuator/health`

---

## No Docker Compose Changes Needed

The health endpoints run on each service's existing port — no new ports or containers are required.
