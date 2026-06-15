# Spring Boot RAG Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable Spring Boot RAG-base project scaffold from the reference POM with updated stable dependencies.

**Architecture:** Use a single Maven module with Spring Boot as the parent build. Keep application code minimal and isolate external infrastructure behind configuration so the scaffold remains easy to extend.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring AI 2.0.0, Maven, PostgreSQL/pgvector, Redis, MinIO, Sa-Token, Actuator, JUnit 5.

---

### Task 1: Create Maven Build

**Files:**
- Create: `pom.xml`

- [ ] Add Spring Boot parent, Spring AI BOM, Java 21, and dependencies matching the reference project.
- [ ] Use latest stable direct versions discovered for MinIO, PDFBox, POI, Sa-Token, and PostgreSQL.

### Task 2: Add Application Skeleton

**Files:**
- Create: `src/main/java/com/trade/ragbase/TradeRagBaseApplication.java`
- Create: `src/main/java/com/trade/ragbase/web/HealthController.java`
- Create: `src/main/resources/application.yml`

- [ ] Add the Spring Boot main class.
- [ ] Add a lightweight `/api/health` endpoint for scaffold validation.
- [ ] Add environment-driven configuration placeholders for AI, datasource, Redis, MinIO, actuator, and Sa-Token.

### Task 3: Add Local Infrastructure and Tests

**Files:**
- Create: `compose.yml`
- Create: `src/test/java/com/trade/ragbase/web/HealthControllerTest.java`
- Modify: `README.md`

- [ ] Add local PostgreSQL with pgvector, Redis, and MinIO services.
- [ ] Add an MVC test for the health endpoint.
- [ ] Document build, test, local infrastructure, and run commands.

### Task 4: Verify

**Files:**
- No file changes.

- [ ] Run `mvn test`.
- [ ] Fix dependency or compile issues if they appear.
