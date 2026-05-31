# NAGP Leave Management Portal

A containerized, event-driven Spring Boot microservices application for managing employee profiles, leave requests, and leave balances. The stack integrates centralized logging (ELK Stack), distributed tracing (Jaeger via OpenTelemetry), asynchronous event-driven messaging (RabbitMQ), and persistent storage (PostgreSQL).

---

## 1. System Architecture

The portal consists of the following microservices and infrastructure components:

```mermaid
flowchart TD
    %% Node Definitions with Devicon/CNCF SVGs
    Client["<img src='https://raw.githubusercontent.com/devicons/devicon/master/icons/chrome/chrome-original.svg' width='30'/> <br/> **Client / Browser** <br/> (Chrome / Postman)"]
    
    subgraph ObsLayer ["🔍 Observability & Tracing"]
        Jaeger["<img src='https://raw.githubusercontent.com/cncf/artwork/main/projects/jaeger/icon/color/jaeger-icon-color.svg' width='30'/> <br/> **Jaeger UI** <br/> Distributed Tracing <br/> `:16686`"]
        Kibana["<img src='https://raw.githubusercontent.com/devicons/devicon/master/icons/kibana/kibana-original.svg' width='30'/> <br/> **Kibana** <br/> Log Visualization <br/> `:5601`"]
    end

    subgraph GatewayLayer ["🚪 Entry & Discovery Layer"]
        Gateway["<img src='https://raw.githubusercontent.com/devicons/devicon/master/icons/spring/spring-original.svg' width='30'/> <br/> **API Gateway** <br/> Spring Cloud Gateway <br/> `:8080`"]
        Eureka["<img src='https://miro.medium.com/v2/resize:fit:640/format:webp/1*eHBmngvJz7eumU5jxzso1w.png' width='30'/> <br/> **Eureka Server** <br/> Service Registry <br/> `:8761`"]
    end

    subgraph ServicesLayer ["⚙️ Core Microservices"]
        AuthService["<img src='https://raw.githubusercontent.com/devicons/devicon/master/icons/spring/spring-original.svg' width='30'/> <br/> **Authentication Service** <br/> `:8081` <br/> <small>(2× replicas)</small>"]
        EmpService["<img src='https://raw.githubusercontent.com/devicons/devicon/master/icons/spring/spring-original.svg' width='30'/> <br/> **Employee Service** <br/> `:8082` <br/> <small>(2× replicas)</small>"]
        LeaveService["<img src='https://raw.githubusercontent.com/devicons/devicon/master/icons/spring/spring-original.svg' width='30'/> <br/> **Leave Management Service** <br/> `:8083` <br/> <small>(2× replicas)</small>"]
        NotifService["<img src='https://raw.githubusercontent.com/devicons/devicon/master/icons/spring/spring-original.svg' width='30'/> <br/> **Notification Service** <br/> `:8084`"]
    end

    subgraph DataLayer ["💾 Persistence Layer"]
        AuthDB[("<img src='https://raw.githubusercontent.com/devicons/devicon/master/icons/postgresql/postgresql-original.svg' width='25'/> <br/> **authdb** <br/> Users Table")]
        EmpDB[("<img src='https://raw.githubusercontent.com/devicons/devicon/master/icons/postgresql/postgresql-original.svg' width='25'/> <br/> **employeedb** <br/> Employees Table")]
        LeaveDB[("<img src='https://raw.githubusercontent.com/devicons/devicon/master/icons/postgresql/postgresql-original.svg' width='25'/> <br/> **leavedb** <br/> Leave Requests & Balances")]
    end

    subgraph MsgLayer ["✉️ Messaging Layer"]
        RabbitMQ[["<img src='https://raw.githubusercontent.com/devicons/devicon/master/icons/rabbitmq/rabbitmq-original.svg' width='30'/> <br/> **RabbitMQ** <br/> `:5672` | UI: `:15672`"]]
    end

    subgraph ELKLayer ["📊 Log Aggregation (ELK Stack)"]
        Filebeat["<img src='https://projects.task.gda.pl/uploads/-/system/project/avatar/476/Beats_Large.png' width='25'/> <br/> **Filebeat** <br/> Log Shipper"]
        Logstash["<img src='https://www.bujarra.com/wp-content/uploads/2018/11/logstash.jpg' width='25'/> <br/> **Logstash** <br/> Pipeline Receiver <br/> `:5044`"]
        ES["<img src='https://raw.githubusercontent.com/devicons/devicon/master/icons/elasticsearch/elasticsearch-original.svg' width='30'/> <br/> **Elasticsearch** <br/> Log Indexer & Store <br/> `:9200`"]
    end

    %% Client entry
    Client -->|"HTTP Requests"| Gateway

    %% Gateway routes
    Gateway -->|"/auth/** (No JWT)"| AuthService
    Gateway -->|"/employees/** (JWT validation)"| EmpService
    Gateway -->|"/leaves/** (JWT validation)"| LeaveService

    %% Eureka registrations
    Gateway -.-|"discovers"| Eureka
    AuthService -.-|"registers"| Eureka
    EmpService -.-|"registers"| Eureka
    LeaveService -.-|"registers"| Eureka
    NotifService -.-|"registers"| Eureka

    %% Database connections
    AuthService --- AuthDB
    EmpService --- EmpDB
    LeaveService --- LeaveDB

    %% Event messaging
    EmpService -->|"Publish: employee.created"| RabbitMQ
    LeaveService -->|"Publish: leave.notification"| RabbitMQ
    RabbitMQ -->|"Consume: employee.created"| LeaveService
    RabbitMQ -->|"Consume: leave.notification"| NotifService

    %% Observability - Traces
    Gateway -.->|"OTLP trace context"| Jaeger
    AuthService -.->|"OTLP trace context"| Jaeger
    EmpService -.->|"OTLP trace context"| Jaeger
    LeaveService -.->|"OTLP trace context"| Jaeger
    NotifService -.->|"OTLP trace context"| Jaeger

    %% Observability - Logs
    Filebeat -->|"Shipped Logs"| Logstash
    Logstash -->|"Indexed Logs"| ES
    ES -->|"Search & Query"| Kibana

    %% Styling classes for premium aesthetic
    classDef client fill:#f5f5f7,stroke:#1d1d1f,stroke-width:2px,color:#1d1d1f;
    classDef obs fill:#f3e8ff,stroke:#7e22ce,stroke-width:2px,color:#581c87;
    classDef gateway fill:#ecfdf5,stroke:#047857,stroke-width:2px,color:#065f46;
    classDef services fill:#eff6ff,stroke:#1d4ed8,stroke-width:2px,color:#1e3a8a;
    classDef data fill:#fff7ed,stroke:#c2410c,stroke-width:2px,color:#7c2d12;
    classDef messaging fill:#fff1f2,stroke:#be123c,stroke-width:2px,color:#881337;
    classDef elk fill:#f0fdfa,stroke:#0f766e,stroke-width:2px,color:#115e59;
    
    class Client client;
    class Jaeger,Kibana obs;
    class Gateway,Eureka gateway;
    class AuthService,EmpService,LeaveService,NotifService services;
    class AuthDB,EmpDB,LeaveDB data;
    class RabbitMQ messaging;
    class Filebeat,Logstash,ES elk;



```

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

For detailed documentation of requests, refer to [POSTMAN DOCUMENTATION](documentation/POSTMAN_DOCUMENTATION.md).

### Seeded Mock Users:
The databases automatically seed the following accounts for testing on initial startup:
* `employee1` (Password: `password`, Role: `EMPLOYEE`)
* `employee2` (Password: `password`, Role: `EMPLOYEE`)
* `manager1` (Password: `password`, Role: `MANAGER`)

### Standard Testing Flow:

#### 1. Authenticate (Get JWT Token)
Send a login request to the gateway to receive your authorization token:
* **Request**: `POST http://localhost:8080/auth/login`
* **Body**:
  ```json
  {
    "username": "employee1",
    "password": "password"
  }
  ```
* **Response**: Save the returned JWT token value.

#### 2. Configure Authorization in Postman
For all subsequent requests, pass the JWT token inside the request header:
* **Header**: `Authorization: Bearer <your_jwt_token>`

#### 3. View Leave Balances
* **Request**: `GET http://localhost:8080/leaves/balances`
* **Headers**: `Authorization: Bearer <token>`
* **Description**: Returns Casual, Sick, and Privilege leave allocations.

#### 4. Apply for Leave
* **Request**: `POST http://localhost:8080/leaves/apply`
* **Headers**: `Authorization: Bearer <token>`
* **Body**:
  ```json
  {
    "leaveType": "CASUAL",
    "startDate": "2026-06-15",
    "endDate": "2026-06-19",
    "reason": "Family vacation",
    "managerId": 3
  }
  ```
* **Description**: Submits a pending request. If valid, the leave days are immediately deducted from the balance.

#### 5. Approve or Reject Leave (Requires Manager Authorization)
Login as `manager1` to get a Manager JWT token, then approve or reject the request:
* **Request**: `POST http://localhost:8080/leaves/{leaveRequestId}/approve` (or `/reject`)
* **Headers**: `Authorization: Bearer <manager_token>`
* **Description**: Sets status to `APPROVED` or `REJECTED`.

---

## 6. Project Documentation

For deeper insights into specific aspects of the architecture, configuration, and implementation, refer to the following guides:

### 📖 Architecture & Design
* [Microservices Design & Decisions](documentation/microservices_design.md) — Detailed design choices, database isolation, service interactions, and data models.
* [Inter-Service Communication](documentation/inter_service_communication.md) — Asynchronous messaging with RabbitMQ, REST APIs, and Eureka Discovery.
* [Database Persistence & Consistency](documentation/database_persistence_and_consistency.md) — PostgreSQL instance isolation, database seeding, and transaction boundaries.

### 🌐 API Reference & Verification
* [API Endpoints Reference](documentation/api_endpoints.md) — Full REST API specifications, query parameters, headers, and payloads.
* [Postman Testing Guide](documentation/POSTMAN_DOCUMENTATION.md) — Step-by-step guide to importing collections and executing end-to-end flows.

### 🛠️ Cross-Cutting Concerns
* [Authentication & Authorization](documentation/cross-cutting-concerns/authentication_and_authorization.md) — JWT generation, role-based controls (Employee vs. Manager), and security filters.
* [Circuit Breaker Pattern](documentation/cross-cutting-concerns/circuit_breaker_pattern.md) — Fault tolerance configuration, rate limits, and fallback controllers using Resilience4j.
* [Global Exception Handling](documentation/cross-cutting-concerns/global_exception_handling.md) — Standardized JSON error response schemas and controller advice classes.
* [Centralized Logging & ELK Stack](documentation/elk_stack_centralized_logging.md) — Step-by-step setup for Filebeat, Logstash, Elasticsearch, and Kibana.
* [Distributed Tracing](documentation/cross-cutting-concerns/distributed_tracing.md) — Trace parent context propagation across services/queues and Jaeger visualizer.
* [Health Checks & Actuator Monitoring](documentation/health_checks.md) — Exposures, indicators, and metrics via Spring Boot Actuator.
* [Logging Details](documentation/cross-cutting-concerns/logging.md) — Logback configuration and service logging levels.