# NAGP Leave Management Portal

A containerized, event-driven Spring Boot microservices application for managing employee profiles, leave requests, and leave balances. The stack integrates centralized logging (ELK Stack), distributed tracing (Jaeger via OpenTelemetry), asynchronous event-driven messaging (RabbitMQ), and persistent storage (PostgreSQL).

---

## 1. System Architecture

The portal consists of the following microservices and infrastructure components:

![Microservice Architecture](documentation/Microservice%20Architecture%20Diagram.png)

### Microservice Summary:
* **`eureka-server`** (Port `8761`): Service registry for dynamic discovery and load balancing. (Docker Image: [dreamspace04/eureka-server](https://hub.docker.com/r/dreamspace04/eureka-server))
* **`api-gateway`** (Port `8080`): Gateway routing requests to downstream services and distributing load between scaled replicas. (Docker Image: [dreamspace04/api-gateway](https://hub.docker.com/r/dreamspace04/api-gateway))
* **`authentication-service`**: Handles JWT issuance, validation, and role-based authorization. (Docker Image: [dreamspace04/authentication-service](https://hub.docker.com/r/dreamspace04/authentication-service))
* **`employee-service`**: Manages employee profiles and hierarchy (manager-employee relationships). (Docker Image: [dreamspace04/employee-service](https://hub.docker.com/r/dreamspace04/employee-service))
* **`leave-management-service`**: Manages leave request processing, balance checks, and concurrency control. (Docker Image: [dreamspace04/leave-management-service](https://hub.docker.com/r/dreamspace04/leave-management-service))
* **`notification-service`**: Asynchronously listens for RabbitMQ events to process alerts. (Docker Image: [dreamspace04/notification-service](https://hub.docker.com/r/dreamspace04/notification-service))

---

## 2. Setup and Prerequisites

### A. Download & Extract Source Code
1. Open the repository on GitHub.
2. Click the green **Code** button and select **Download ZIP** (or clone the repository using `git clone <repository_url>`).
3. Extract the downloaded ZIP archive to a folder on your host machine.

### B. Required System Tools
Ensure the following tools are installed and configured on your host system:
* **Java Development Kit (JDK) 17** (Verify with `java -version` and ensure `JAVA_HOME` is set)
* **Apache Maven 3.8+** (Verify with `mvn -version`)
* **Docker Engine** & **Docker Compose** (Ensure the Docker Desktop or daemon is active)

### C. IDE Configuration (Project Lombok Support)
The codebase uses **Project Lombok** to generate boilerplates (like getters, setters, constructors, and SLF4J loggers) at compile time.
* **No manual JAR downloads are required**: The dependency is managed automatically by Maven via the parent [pom.xml](pom.xml#L87-L90) and is fetched during compile time.
* **Enable Annotation Processing**: To avoid syntax highlighting and compilation errors in your IDE (like IntelliJ IDEA or Eclipse), you must enable annotation processors:
  * **IntelliJ IDEA**: Open `Settings` (or `Preferences` on macOS) $\rightarrow$ `Build, Execution, Deployment` $\rightarrow$ `Compiler` $\rightarrow$ `Annotation Processors`. Check the box for **`Enable annotation processing`** and click OK.
  * **Lombok Plugin**: Make sure the Lombok plugin is installed and active in your IDE (pre-installed by default in modern IntelliJ IDEA versions).

---

## 3. How to Run with Docker Compose

Follow these steps to compile, package, and boot up the complete microservices stack:

### Step 1: Package the Microservices
Navigate to the root directory of the extracted project in your terminal and compile the packages:
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
