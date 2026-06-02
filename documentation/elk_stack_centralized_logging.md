# Centralized Logging – ELK Stack Implementation

This document explains how **centralized log aggregation** is configured and implemented across all microservices in the Employee Leave Portal using the **ELK Stack** — Elasticsearch, Logstash, Kibana — with Filebeat as the log shipper.

---


## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  Each Microservice (6 services)                                      │
│                                                                      │
│  [Logback + logstash-logback-encoder]                                │
│       │  Emits structured JSON to stdout                             │
│       ▼                                                              │
│  [Docker container stdout/stderr]                                    │
└───────────────┬──────────────────────────────────────────────────────┘
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

## Services In Scope that emits log 

| Service 
|---|
| `api-gateway` | 
| `authentication-service` | 
| `employee-service` | 
| `leave-management-service` | 
| `notification-service` | 
| `eureka-server` | 

---

## Implementation Details

The centralized logging pipeline is configured through the following key components:

### 1. Parent POM Dependency
Every microservice imports `net.logstash.logback:logstash-logback-encoder` to format log outputs as structured JSON.

### 2. Profile-Switched Logback Configurations (`logback-spring.xml`)
Each microservice is configured with two log appenders:
* **`CONSOLE` appender (active during local dev / `!docker` profile)**: Human-readable text format.
* **`JSON_STDOUT` appender (active during containerized run / `docker` profile)**: Outputs structured JSON containing timestamps, log severity levels, message text, logger class, thread details, application names, and container hostname/instance details.

### 3. Log Levels (`application.yml`)
Core application code (`com.niloy.*`) is configured to log at the `DEBUG` level, while third-party framework classes (like Spring Framework and Hibernate internals) are capped at `WARN` to minimize log volume noise.

### 4. Logstash Filtering Pipeline (`logstash.conf`)
Logstash listens on port `5044` for incoming beats and processes them through three stages:
* **Input**: Accepts log events forwarded by Filebeat.
* **Filter**: Automatically parses the JSON payloads from incoming messages, synchronizes event timestamps with Elasticsearch, and hoists custom attributes (like service name, trace ID, and log level) to the root document level for efficient indexing.
* **Output**: Routes the parsed and indexed events to Elasticsearch under a rolling daily index name (`leave-portal-logs-YYYY.MM.dd`).

### 5. Filebeat Log Collection (`filebeat.yml`)
Filebeat runs as a daemon log shipper that reads log files from `/var/lib/docker/containers/`. It is configured to:
* Automatically discover running Docker containers.
* Filter and collect logs exclusively from containers running image names matching `dreamspace04/*` (avoiding background noise from message brokers and database containers).
* Inject host and Docker container metadata (like container name and ID) into every log trace.

### 6. Orchestrated Container Configurations (`docker-compose.yml`)
ELK stack services are integrated into the main `docker-compose.yml` stack:
* **Elasticsearch**: Persists indexed logs to a named volume (`esdata`) in a single-node setup with security disabled for local development.
* **Logstash & Kibana**: Configured to wait for Elasticsearch to become healthy via container health checks (`service_healthy`) before startup.
* **Microservices**: Run with `SPRING_PROFILES_ACTIVE=docker` to switch their Logback outputs from console-friendly text to JSON stream formatting.

---

## Environment: Local Dev vs. Docker

| Aspect | Local Dev (IDE / Maven) | Docker (`docker-compose up`) |
|---|---|---|
| Active Spring Profile | _(none / default)_ | `docker` |
| Logback Appender | `CONSOLE` — human-readable | `JSON_STDOUT` — structured JSON |
| Log destination | Terminal stdout | Docker container stdout → Filebeat → Logstash → Elasticsearch |
| Log format | `2026-05-28 10:22:01 [main] INFO  ...` | `{"@timestamp":"...","level":"INFO","service":"...","message":"..."}` |
| Log searchable in Kibana | ❌ No | ✅ Yes |

No configuration file changes are needed when switching environments — the Spring profile switch is handled entirely by the `SPRING_PROFILES_ACTIVE` environment variable in `docker-compose.yml`.

---

## JSON Log Event Structure

When running in Docker, every log event emitted by a service looks like this:

```json
{
  "@timestamp": "2026-05-28T16:22:01.412Z",
  "level": "INFO",
  "message": "Leave applied successfully — leaveId=12, employeeId=1",
  "logger": "com.niloy.leave.controller.LeaveController",
  "thread": "reactor-http-nio-3",
  "service": "leave-management-service",
  "instance": "a3f89c7d8e2f",
  "host": {
    "name": "docker-host"
  },
  "container": {
    "id": "a3f89c...",
    "name": "leave-management-service"
  }
}
```

> **Note on `instance` field**: The `instance` field is populated from `${HOSTNAME}` (the container ID prefix), allowing you to distinguish log events from different replicas of the same scaled service (e.g., `authentication-service` runs 2 replicas by default).

The `service` field makes it trivial to filter by a specific microservice in Kibana.

---

## How to Use Kibana

### Step 1: Start all services

```bash
docker-compose up --build -d
```

### Step 2: Open Kibana

Navigate to **`http://localhost:5601`** in your browser.

### Step 3: Create an Index Pattern (First Time Only)

1. Click the **☰ menu** → **Stack Management**
2. Under **Kibana**, click **Index Patterns** (or **Data Views** in newer versions)
3. Click **Create index pattern**
4. Enter pattern: `leave-portal-logs-*`
5. Set **Time field**: `@timestamp`
6. Click **Create index pattern**

### Step 4: Explore Logs in Discover

1. Click **☰ menu** → **Discover**
2. Ensure `leave-portal-logs-*` is selected in the top-left dropdown
3. Set your time range (e.g., **Last 15 minutes**)

---

## Troubleshooting

### No logs appearing in Kibana

1. **Check Elasticsearch is running:**
   ```bash
   curl http://localhost:9200
   ```
   Expected: `{"name":"...","cluster_name":"docker-cluster",...}`

2. **Check indices were created:**
   ```bash
   curl http://localhost:9200/_cat/indices?v
   ```
   Expected: `leave-portal-logs-YYYY.MM.DD` listed with a green/yellow health status.

3. **Check Logstash is receiving events:**
   ```bash
   docker logs logstash --tail 50
   ```
   Look for `rubydebug` output showing parsed log events.

4. **Check Filebeat is running:**
   ```bash
   docker logs filebeat --tail 50
   ```
   Look for lines like `"Published X events"`.

5. **Verify `SPRING_PROFILES_ACTIVE=docker`** is set on your service containers:
   ```bash
   docker inspect leave-management-service | grep SPRING_PROFILES
   ```

### Kibana shows "No results match your search criteria"

- Widen the time range filter (top-right of Discover page).
- Make sure the index pattern `leave-portal-logs-*` was created with `@timestamp` as the time field.
- Trigger some API calls to generate log events (see the [API endpoints documentation](/documentation/api_endpoints.md)).

### Logstash fails to start

Logstash depends on Elasticsearch being healthy. Check:
```bash
docker-compose ps
```
If Elasticsearch shows `unhealthy`, check its logs:
```bash
docker logs elasticsearch --tail 50
```
Common cause: insufficient Docker memory. Increase Docker Desktop memory allocation to at least **4 GB**.

---
