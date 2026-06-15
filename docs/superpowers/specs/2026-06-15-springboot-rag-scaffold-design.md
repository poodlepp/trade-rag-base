# Spring Boot RAG Scaffold Design

## Goal

Create a Spring Boot scaffold for `trade-rag-base` based on the reference `jc-rag-kb` POM, keeping the same RAG-oriented dependency surface while updating direct dependency versions to current stable releases where practical.

## Approach

The scaffold uses Spring Boot 4.1.0, Java 21, and Spring AI 2.0.0. It includes starters and libraries for web APIs, Spring AI OpenAI-compatible models, JPA/PostgreSQL with Hibernate vector support, Redis, MinIO, document parsing, token counting, Sa-Token, actuator metrics, retry, and tests.

The initial code is intentionally small: one application entrypoint, one health/info controller, default YAML configuration, a local infrastructure compose file, and a web-layer test. Database, Redis, MinIO, and AI credentials are configured through environment variables so the repository can be cloned without embedding secrets.

## Files

- `pom.xml`: Maven build, dependency management, and direct library versions.
- `src/main/java/com/trade/ragbase/TradeRagBaseApplication.java`: Spring Boot entrypoint.
- `src/main/java/com/trade/ragbase/web/HealthController.java`: Lightweight scaffold endpoint.
- `src/main/resources/application.yml`: Default configuration and environment placeholders.
- `compose.yml`: Optional local PostgreSQL/pgvector, Redis, and MinIO services.
- `src/test/java/com/trade/ragbase/web/HealthControllerTest.java`: MVC smoke test.
- `README.md`: Project usage, dependency notes, and local run commands.

## Verification

Run `mvn test` to verify dependency resolution and the scaffold web test. Run `mvn spring-boot:run` after providing the required local infrastructure and environment variables.
