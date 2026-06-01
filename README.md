# NAGP Leave Management Portal

A containerized, event-driven Spring Boot microservices application for managing employee profiles, leave requests, and leave balances. The stack integrates centralized logging (ELK Stack), distributed tracing (Jaeger via OpenTelemetry), asynchronous event-driven messaging (RabbitMQ), and persistent storage (PostgreSQL).

---

## 1. System Architecture

The portal consists of the following microservices and infrastructure components:

![Microservice Architecture](documentation/Microservice%20Architecture%20Diagram.png)

### Microservice Summary:
* **`eureka-server`** (Port `8761`): Service registry for dynamic discovery and load balancing.
* **`api-gateway`** (Port `8080`): Gateway routing requests to downstream services and distributing load between scaled replicas.
* **`authentication-service`**: Handles JWT issuance, validation, and role-based authorization.
* **`employee-service`**: Manages employee profiles and hierarchy (manager-employee relationships).
* **`leave-management-service`**: Manages leave request processing, balance checks, and concurrency control.
* **`notification-service`**: Asynchronously listens for RabbitMQ events to process alerts.

---

## 2. Setup and Prerequisites

Ensure the following tools are installed on your host system:
* **Java Development Kit (JDK) 17**
* **Apache Maven 3.8+**
* **Docker Engine** & **Docker Compose**

---

## 3. How to Run with Docker Compose

Follow these steps to compile and spin up the complete infrastructure stack:

### Step 1: Package the Microservices
Build the Java packages using Maven:
```bash
mvn clean package -DskipTests
```

### Step 2: Boot the Infrastructure and Containers
Launch all services in detached mode:
```bash
docker-compose up --build -d
```

### Step 3: Verify Container Health
Check the container status to verify they are healthy and running:
```bash
docker-compose ps
```

### Step 4: Access System Dashboards
* **Eureka Service Registry**: [http://localhost:8761](http://localhost:8761)
* **Kibana (Centralized Logs)**: [http://localhost:5601](http://localhost:5601)
* **Jaeger (Distributed Tracing)**: [http://localhost:16686](http://localhost:16686)
* **RabbitMQ Management**: [http://localhost:15672](http://localhost:15672) (User: `guest`, Pass: `guest`)

To shut down the environment:
```bash
docker-compose down
```
To shut down and wipe persistent volumes (re-seeding databases on next boot):
```bash
docker-compose down -v
```

---

## 4. Environment Variables Needed

The services are parameterized to support easy overrides. When deployed in Docker, these variables are injected dynamically.

| Variable Name | Description | Default Value (Local Fallback) |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Active Spring application profiles | `default` (H2 fallback deactivated, PostgreSQL driver used) |
| `SPRING_DATASOURCE_URL` | Database connection URL | `jdbc:postgresql://localhost:5432/<db_name>` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `admin` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `admin` |
| `SPRING_JPA_DATABASE_PLATFORM`| Dialect platform class | `org.hibernate.dialect.PostgreSQLDialect` |
| `SPRING_RABBITMQ_HOST` | Host address of RabbitMQ broker | `localhost` |
| `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE` | Endpoint for service registration | `http://localhost:8761/eureka/` |
| `MANAGEMENT_OTLP_TRACING_ENDPOINT` | OpenTelemetry endpoint for tracing | `http://localhost:4318/v1/traces` |

---

## 5. API Testing Instructions

An exportable Postman collection is included directly in the root directory:
* [Leave_Portal.postman_collection.json](Leave_Portal.postman_collection.json)

For detailed documentation of requests, refer to [POSTMAN DOCUMENTATION](documentation/postman_collection_documentation.md).
---

## 6. Project Documentation

For deeper insights into specific aspects of the architecture, configuration, and implementation, refer to the following guides:

### 📖 Architecture & Design
* [Microservices Design & Decisions](documentation/microservices_design.md) — Detailed design choices, database isolation, service interactions, and data models.
* [Inter-Service Communication](documentation/inter_service_communication.md) — Asynchronous messaging with RabbitMQ, REST APIs, and Eureka Discovery.
* [Database Persistence & Consistency](documentation/database_persistence_and_consistency.md) — PostgreSQL instance isolation, database seeding, and transaction boundaries.

### 🌐 API Reference & Verification
* [API Endpoints Reference](documentation/api_endpoints.md) — Full REST API specifications, query parameters, headers, and payloads.
* [Postman Testing Guide](documentation/POSTMAN_DOCUMENTATION.md) — Step-by-step guide to import collections and executing end-to-end flows.

### 🛠️ Cross-Cutting Concerns
* [Authentication & Authorization](documentation/cross-cutting-concerns/authentication_and_authorization.md) — JWT generation, role-based controls (Employee vs. Manager), and security filters.
* [Circuit Breaker Pattern](documentation/cross-cutting-concerns/circuit_breaker_pattern.md) — Fault tolerance configuration, rate limits, and fallback controllers using Resilience4j.
* [Global Exception Handling](documentation/cross-cutting-concerns/global_exception_handling.md) — Standardized JSON error response schemas and controller advice classes.
* [Centralized Logging & ELK Stack](documentation/elk_stack_centralized_logging.md) — Step-by-step setup for Filebeat, Logstash, Elasticsearch, and Kibana.
* [Distributed Tracing](documentation/cross-cutting-concerns/distributed_tracing.md) — Trace parent context propagation across services/queues and Jaeger visualizer.
* [Health Checks & Actuator Monitoring](documentation/health_checks.md) — Exposures, indicators, and metrics via Spring Boot Actuator.
* [Logging Details](documentation/cross-cutting-concerns/logging.md) — Logback configuration and service logging levels.
