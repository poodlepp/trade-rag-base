# trade-rag-base

Spring Boot RAG base scaffold for trade knowledge applications.

## Stack

- Java 21
- Spring Boot 4.1.0
- Spring AI 2.0.0
- PostgreSQL + pgvector
- Redis
- MinIO
- Sa-Token
- Actuator + Prometheus

## Project Structure

```text
src/main/java/com/trade/ragbase
  TradeRagBaseApplication.java
  web/HealthController.java
src/main/resources/application.yml
src/test/java/com/trade/ragbase/web/HealthControllerTest.java
compose.yml
pom.xml
```

## Local Infrastructure

Start PostgreSQL, Redis, and MinIO:

```bash
docker compose up -d
```

MinIO console:

```text
http://localhost:9001
```

Default MinIO credentials are `minioadmin` / `minioadmin`.

## Build and Test

```bash
mvn test
```

## Run

Set AI credentials if you want Spring AI calls to work:

```bash
export OPENAI_API_KEY=your-api-key
export OPENAI_BASE_URL=https://api.openai.com
```

Then start the app:

```bash
mvn spring-boot:run
```

Health endpoint:

```text
GET http://localhost:8080/api/health
```
