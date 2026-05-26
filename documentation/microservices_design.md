# Microservices Design Document

This document outlines the system architecture and microservices design for the **Project  Employee Leave Management Portal**.

---

## 1. System Architecture

The portal is designed using a cloud-native **Microservices Architecture**. The system components register themselves with a Service Registry, route incoming client requests through a centralized API Gateway, and interact asynchronously using an event-driven model.

### Architecture Diagram

The diagram below visualizes the system's runtime architecture, detailing routing, service discovery, databases, and messaging paths.

```mermaid
flowchart TD
    Client["Client App / Postman"]
    
    subgraph GatewayLayer ["API Gateway & Discovery"]
        Gateway["Spring Cloud Gateway (Port 8080)"]
        Eureka["Netflix Eureka Server (Port 8761)"]
    end
    
    subgraph ServicesLayer ["Core Microservices"]
        AuthService["Authentication Service (Port 8081)"]
        EmpService["Employee Service (Port 8082)"]
        LeaveService["Leave Management Service (Port 8083)"]
        NotifService["Notification Service (Port 8084)"]
    end

    subgraph DatabaseLayer ["Mock Databases (H2 In-Memory)"]
        AuthDB[("Auth DB")]
        EmpDB[("Employee DB")]
        LeaveDB[("Leave DB")]
    end

    subgraph MessageBroker ["Messaging Layer"]
        RabbitMQ[["RabbitMQ Broker"]]
    end

    %% Client Routing
    Client -->|HTTP Requests| Gateway
    
    %% Gateway Routing (Load Balanced)
    Gateway -->|/auth/**| AuthService
    Gateway -->|/employees/** (with JWT Header)| EmpService
    Gateway -->|/leaves/** (with JWT Header)| LeaveService

    %% Service Registrations
    Gateway -.->|Registers/Discovers| Eureka
    AuthService -.->|Registers| Eureka
    EmpService -.->|Registers| Eureka
    LeaveService -.->|Registers| Eureka
    NotifService -.->|Registers| Eureka

    %% Database connections
    AuthService === AuthDB
    EmpService === EmpDB
    LeaveService === LeaveDB

    %% Event Messaging
    EmpService -->|Publish employee.created| RabbitMQ
    LeaveService -->|Publish leave.notification| RabbitMQ
    RabbitMQ -->|Consume employee.created| LeaveService
    RabbitMQ -->|Consume leave.notification| NotifService

    %% Output
    NotifService -->|Logs Sim| Console["System Console / Logs"]
```

---

## 2. Microservice Components

### API Gateway (`api-gateway`)
- **Technology**: Spring Cloud Gateway
- **Responsibility**: Single entrypoint for all client apps. It performs centralized **JWT Token Validation** via a custom filter. Valid tokens are parsed, and the user's details (`userId`, `role`, `username`) are forwarded downstream as custom HTTP headers:
  - `X-User-Id`
  - `X-User-Role`
  - `X-User-Username`

### Service Discovery (`eureka-server`)
- **Technology**: Spring Cloud Netflix Eureka Server
- **Responsibility**: Registry where all microservices dynamically register their network location (IP/port) upon startup. Enables gateway load balancing without hardcoded host names.

### Authentication Service (`authentication-service`)
- **Technology**: Spring Boot, Spring Security, JPA, H2 Database
- **Responsibility**: Verifies user login credentials (using `BCryptPasswordEncoder`) against `Auth DB` and generates cryptographic JWT Bearer Tokens.

### Employee Service (`employee-service`)
- **Technology**: Spring Boot, Spring AMQP, JPA, H2 Database
- **Responsibility**: Manages employee profiles and reporting lines. Upon creating a new employee profile, it publishes an `employee.created` event to RabbitMQ.

### Leave Management Service (`leave-management-service`)
- **Technology**: Spring Boot, Spring AMQP, JPA, H2 Database
- **Responsibility**:
  - Automatically initializes leave balances (12 Casual, 10 Sick, 15 Privilege) by listening to `employee.created` events.
  - Processes leave applications with business validation (insufficient balance, overlapping dates, past date checks).
  - Handles approval/rejection workflows and publishes notifications to RabbitMQ.

### Notification Service (`notification-service`)
- **Technology**: Spring Boot, Spring AMQP
- **Responsibility**: Consumes leave application and status changes from RabbitMQ and logs simulation notification outputs directly to the system console/logs.
