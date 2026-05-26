# ELK Stack – Centralized Logging Implementation Plan
## Employee Leave Management Portal

---

## Overview

Currently the project uses **Jaeger** for distributed tracing but has **no centralized log aggregation**. Each service prints logs independently to stdout/container console, making it impossible to search across services or correlate errors.

This plan implements the **ELK Stack** (Elasticsearch + Logstash + Kibana) with **Filebeat** as the log shipper. Every microservice will emit structured JSON logs that are automatically collected, indexed, and made searchable in Kibana.

---

## Architecture After Implementation

```
┌─────────────────────────────────────────────────────────────────────┐
│  Each Microservice (6 services)                                      │
│                                                                     │
│  [Logback + logstash-logback-encoder]                               │
│       │  Emits structured JSON to stdout                            │
│       ▼                                                             │
│  [Docker container stdout/stderr]                                   │
└───────────────┬─────────────────────────────────────────────────────┘
                │  Docker log driver / log files
                ▼
        ┌───────────────┐
        │   Filebeat    │  ← Collects container logs, adds metadata
        │  (log shipper)│     (service name, container id, etc.)
        └───────┬───────┘
                │  Ships to Logstash :5044
                ▼
        ┌───────────────┐
        │   Logstash    │  ← Parses / enriches / filters log events
        └───────┬───────┘
                │  Indexes to Elasticsearch :9200
                ▼
        ┌───────────────┐
        │ Elasticsearch │  ← Stores and indexes all logs
        └───────┬───────┘
                │
                ▼
        ┌───────────────┐
        │    Kibana     │  ← Search, visualize, alert on logs
        │   :5601       │     (Discover, Dashboards, Lens)
        └───────────────┘
```

---

## Services In Scope

| Service | Port | Will Emit JSON Logs |
|---------|------|---------------------|
| `api-gateway` | 8080 | ✅ Yes |
| `authentication-service` | 8081 | ✅ Yes |
| `employee-service` | 8082 | ✅ Yes |
| `leave-management-service` | 8083 | ✅ Yes |
| `notification-service` | 8084 | ✅ Yes |
| `eureka-server` | 8761 | ✅ Yes |

---

## Files to Create / Modify

### 1. Parent POM (`pom.xml`) — [MODIFY]
Add `logstash-logback-encoder` to the shared `<dependencyManagement>` block so all child modules can inherit it.

```xml
<!-- In <dependencyManagement> -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

Also add it to the shared `<dependencies>` block so every module gets it automatically:

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
</dependency>
```

---

### 2. `logback-spring.xml` — [NEW] × 6 services

One file per service under `src/main/resources/`. Each file configures two appenders:
- **CONSOLE** – human-readable output for local development
- **JSON_STDOUT** – structured JSON output consumed by Filebeat in Docker

**Template** (service-specific `<springProperty>` for the service name):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <!-- Read the Spring app name for the "service" JSON field -->
    <springProperty scope="context" name="APP_NAME" source="spring.application.name"/>

    <!-- Human-readable console (local dev) -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Structured JSON to stdout (Docker / Filebeat) -->
    <appender name="JSON_STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${APP_NAME}"}</customFields>
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <message>message</message>
                <logger>logger</logger>
                <thread>thread</thread>
                <level>level</level>
            </fieldNames>
        </encoder>
    </appender>

    <!-- Use JSON in Docker profile, console otherwise -->
    <springProfile name="docker">
        <root level="INFO">
            <appender-ref ref="JSON_STDOUT"/>
        </root>
    </springProfile>
    <springProfile name="!docker">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

</configuration>
```

Files to create:
- `api-gateway/src/main/resources/logback-spring.xml`
- `authentication-service/src/main/resources/logback-spring.xml`
- `employee-service/src/main/resources/logback-spring.xml`
- `leave-management-service/src/main/resources/logback-spring.xml`
- `notification-service/src/main/resources/logback-spring.xml`
- `eureka-server/src/main/resources/logback-spring.xml`

---

### 3. Each service's `application.yml` — [MODIFY] × 6 services

Add logging level configuration so meaningful INFO/WARN/ERROR logs are emitted per service:

```yaml
logging:
  level:
    root: INFO
    com.niloy: DEBUG   # full debug for your own packages
```

---

### 4. `docker-compose.yml` — [MODIFY]

Add four new containers: `elasticsearch`, `logstash`, `kibana`, `filebeat`.

```yaml
  # ── ELK Stack ──────────────────────────────────────────────────────────────

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.13.0
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false   # disabled for dev simplicity
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
    volumes:
      - esdata:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9200 || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10

  logstash:
    image: docker.elastic.co/logstash/logstash:8.13.0
    container_name: logstash
    ports:
      - "5044:5044"   # Beats input
      - "9600:9600"   # Logstash API
    volumes:
      - ./elk/logstash/pipeline:/usr/share/logstash/pipeline:ro
    depends_on:
      elasticsearch:
        condition: service_healthy

  kibana:
    image: docker.elastic.co/kibana/kibana:8.13.0
    container_name: kibana
    ports:
      - "5601:5601"
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    depends_on:
      elasticsearch:
        condition: service_healthy

  filebeat:
    image: docker.elastic.co/beats/filebeat:8.13.0
    container_name: filebeat
    user: root
    volumes:
      - ./elk/filebeat/filebeat.yml:/usr/share/filebeat/filebeat.yml:ro
      - /var/lib/docker/containers:/var/lib/docker/containers:ro
      - /var/run/docker.sock:/var/run/docker.sock:ro
    depends_on:
      - logstash

volumes:
  esdata:
    driver: local
```

Also add `SPRING_PROFILES_ACTIVE=docker` to each microservice's `environment:` block so the JSON Logback profile activates.

---

### 5. `elk/logstash/pipeline/logstash.conf` — [NEW]

Logstash pipeline: receives Beats input, parses JSON, ships to Elasticsearch.

```
input {
  beats {
    port => 5044
  }
}

filter {
  # The message field is already a JSON string from LogstashEncoder
  if [message] =~ /^\{/ {
    json {
      source => "message"
      target => "log"
    }
    mutate {
      add_field => { "service" => "%{[log][service]}" }
    }
  }

  # Add readable date from @timestamp
  date {
    match => ["[log][@timestamp]", "ISO8601"]
    target => "@timestamp"
  }
}

output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    index => "leave-portal-logs-%{+YYYY.MM.dd}"
  }
  stdout {
    codec => rubydebug
  }
}
```

---

### 6. `elk/filebeat/filebeat.yml` — [NEW]

Filebeat configuration to auto-discover Docker container logs and tag them with the container/service name.

```yaml
filebeat.autodiscover:
  providers:
    - type: docker
      hints.enabled: true
      templates:
        - condition:
            contains:
              docker.container.labels.com.docker.compose.project: nagp2026
          config:
            - type: container
              paths:
                - /var/lib/docker/containers/${data.docker.container.id}/*.log
              processors:
                - add_docker_metadata:
                    host: "unix:///var/run/docker.sock"

processors:
  - add_host_metadata: ~

output.logstash:
  hosts: ["logstash:5044"]

logging.level: info
```

---

## Folder Structure After Implementation

```
g:\NAGP2026\
├── elk\
│   ├── logstash\
│   │   └── pipeline\
│   │       └── logstash.conf           ← NEW
│   └── filebeat\
│       └── filebeat.yml                ← NEW
├── api-gateway\src\main\resources\
│   ├── application.yml                 ← MODIFY (add logging levels + docker profile)
│   └── logback-spring.xml              ← NEW
├── authentication-service\src\main\resources\
│   ├── application.yml                 ← MODIFY
│   └── logback-spring.xml              ← NEW
├── employee-service\src\main\resources\
│   ├── application.yml                 ← MODIFY
│   └── logback-spring.xml              ← NEW
├── leave-management-service\src\main\resources\
│   ├── application.yml                 ← MODIFY
│   └── logback-spring.xml              ← NEW
├── notification-service\src\main\resources\
│   ├── application.yml                 ← MODIFY
│   └── logback-spring.xml              ← NEW
├── eureka-server\src\main\resources\
│   ├── application.yml                 ← MODIFY
│   └── logback-spring.xml              ← NEW
├── docker-compose.yml                  ← MODIFY (add ELK containers)
└── pom.xml                             ← MODIFY (add logstash-logback-encoder)
```

---

## Port Reference

| Service | Port | Purpose |
|---------|------|---------|
| Elasticsearch | 9200 | REST API / data store |
| Logstash | 5044 | Beats input from Filebeat |
| Logstash | 9600 | Logstash monitoring API |
| Kibana | 5601 | Web UI for log search & dashboards |

---

## Kibana Setup Steps (After Containers Start)

1. Open `http://localhost:5601`
2. Go to **Stack Management → Index Patterns**
3. Create index pattern: `leave-portal-logs-*`
4. Set time field: `@timestamp`
5. Go to **Discover** → search across all services instantly
6. Useful filters in Kibana:
   - Filter by `service: leave-management-service` to see only leave logs
   - Filter by `level: ERROR` to find all errors across all services
   - Combine with `traceId` from Jaeger for full request correlation

---

## What Each Service Will Log

| Service | Key Log Events |
|---------|---------------|
| `api-gateway` | Incoming requests, JWT validation, CB state transitions, fallback triggers |
| `authentication-service` | Login attempts, token generation, failed authentications |
| `employee-service` | Employee CRUD operations, `employee.created` RabbitMQ publishes |
| `leave-management-service` | Leave applications, approvals/rejections, balance updates, RabbitMQ events |
| `notification-service` | Consumed `leave.notification` events, simulated notification logs |
| `eureka-server` | Service registrations and deregistrations |

---

## Implementation Order

1. `pom.xml` – add `logstash-logback-encoder` dependency
2. `logback-spring.xml` – create for all 6 services
3. Each `application.yml` – add `logging:` block + `docker` profile
4. `elk/logstash/pipeline/logstash.conf` – create Logstash pipeline
5. `elk/filebeat/filebeat.yml` – create Filebeat config
6. `docker-compose.yml` – add ELK containers + `SPRING_PROFILES_ACTIVE=docker` to each service
7. Rebuild Docker images and test in Kibana
