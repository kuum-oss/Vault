# Vault

[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.0-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-4.0.2-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Build](https://img.shields.io/badge/build-Maven-007396?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

Vault — учебный fintech-проект на Java 25. Репозиторий показывает границы сервисов для управления счетами, переводами и аутентификацией: WebFlux, JWE/JWS, TOTP, Kafka, Liquibase, Spring Cloud Gateway, Config Server и Eureka.

> Статус: **development / learning project**. API и доменная модель запускаются локально, но это не платёжная система и не готовый production-сервис. Реальный persistence, KMS и transactional outbox описаны в roadmap.

## Навигация

- [Архитектура](docs/ARCHITECTURE.md) — компоненты, потоки запросов и событий, границы ответственности.
- [Стек и зависимости](docs/DEPENDENCIES.md) — фактически подключённые библиотеки, инфраструктура и статус использования.
- [Production roadmap](docs/ARCHITECTURE.md#production-roadmap) — R2DBC, KMS, outbox, retry/DLT и release gates.
- [Kubernetes Secret template](docker/k8s/vault-secrets.example.yaml) — только шаблон, без значений секретов.

## Модули

| Module | Port | Responsibility | Current state |
| --- | ---: | --- | --- |
| `vault-gateway` | 8080 | Routes, JWE/JWS validation, identity headers | Implemented; Redis rate limiting is not wired yet. |
| `vault-auth` | 8081 | Registration, login, TOTP and opaque access token | Implemented for local demo; users are in memory. |
| `vault-accounts` | 8082 | Open/list/freeze accounts, initiated-transfer consumer | In-memory API; Liquibase/R2DBC dependencies are prepared. |
| `vault-transfers` | 8083 | Transfer draft/submit and transfer event producer | In-memory state; transition logic is implemented. |
| `vault-reports` | 8084 | Summary/transaction report endpoints | Stub; Oracle integration is not implemented. |
| `vault-config-server` | 8888 | Native Spring Cloud Config Server | Implemented, not required by local quick start. |
| `vault-discovery` | 8761 | Eureka registry | Implemented, not required by local quick start. |
| `vault-domain` | — | Shared immutable domain records and transition rules | Implemented and unit-tested. |

## Quick start

Prerequisites: JDK 25, Maven 3.9+, Docker Desktop. The first Maven run needs Internet access to download dependencies.

```bash
docker compose -f docker/docker-compose.yml up -d
mvn clean verify
```

Start the services in separate terminals:

```bash
mvn -pl vault-auth spring-boot:run
mvn -pl vault-accounts spring-boot:run
mvn -pl vault-transfers spring-boot:run
mvn -pl vault-reports spring-boot:run
mvn -pl vault-gateway spring-boot:run
```

Then check gateway health:

```bash
curl http://localhost:8080/actuator/health
```

On Apple Silicon, gateway includes the `osx-aarch_64` Netty DNS resolver. The local Kafka image is the official multi-architecture `apache/kafka:4.0.2` image.

## Local API flow

```bash
# 1. Register. Save `totpSecret` in Google Authenticator manually or generate a code with a TOTP tool.
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@vault.local","password":"change-me"}'

# 2. Request the TOTP challenge.
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@vault.local","password":"change-me"}'

# 3. Verify a current six-digit code and receive the opaque JWE access token.
curl -X POST http://localhost:8080/auth/verify-totp \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@vault.local","code":"123456"}'
```

The auth application generates an ephemeral RSA key pair when the `dev` profile is active. Therefore tokens are invalid after it restarts. Gateway allows unauthenticated traffic only when no RSA keys are configured locally; this convenience must never be used in production.

## Configuration and secrets

Copy [`.env.example`](.env.example) only for local values; do not commit the resulting `.env`. In a non-`dev` profile both auth and gateway require these environment variables:

```text
VAULT_RSA_PRIVATE_PEM=<PKCS#8 PEM>
VAULT_RSA_PUBLIC_PEM=<X.509 PEM>
VAULT_DB_PASSWORD=<secret>
```

For Kubernetes, inject them from a managed secret store. The checked-in [Secret manifest](docker/k8s/vault-secrets.example.yaml) is an intentionally unusable template.

## What is implemented vs planned

Implemented: HTTP routes listed above, immutable `Account`/`Transfer`/`AuditLog` records, transition rules, BCrypt password hashes, TOTP verification, JWS inside JWE, gateway validation, Liquibase changelogs, Docker dependencies and one Kafka producer/consumer path.

Planned: persistent R2DBC repositories, a configured Spring State Machine (the current transition engine is a pure domain method), Oracle reports, Redis rate limiting, tracing export, service registration/config client wiring, KMS encryption at rest, transactional outbox and complete Kubernetes manifests. See [architecture](docs/ARCHITECTURE.md#production-roadmap) for acceptance criteria.

## Verification

```bash
mvn clean verify
docker compose -f docker/docker-compose.yml config -q
```

If Maven reports a missing artifact while using `-o`, run the same command once without offline mode. The project deliberately has no CI badge until a CI workflow is committed; the badges above identify the pinned runtime stack, not a build result.
