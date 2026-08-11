# Stack and dependencies

This document is the source of truth for the tools currently declared in Maven or Docker Compose. “Prepared” means a dependency/schema exists but the application adapter is not complete.

## Runtime

| Area | Technology | Version | Used by | Status |
| --- | --- | --- | --- | --- |
| Language | Java | 25 | all Java modules | Active |
| Framework | Spring Boot | 3.5.3 | all applications | Active |
| Cloud BOM | Spring Cloud | 2025.0.0 | gateway/config/discovery | Active |
| Reactive HTTP | Spring WebFlux / Reactor Netty | Boot-managed | auth, accounts, transfers, reports | Active |
| Gateway | Spring Cloud Gateway | Cloud-managed | gateway | Active |
| Service discovery | Eureka Server | Cloud-managed | discovery | Server only; clients not wired |
| Configuration | Spring Cloud Config Server | Cloud-managed | config-server | Native server only; clients not wired |
| Metrics | Actuator | Boot-managed | auth, accounts, reports, gateway | Health/info exposure active; `prometheus` is configured for exposure but a Prometheus registry dependency is still needed |
| Tokens | Nimbus JOSE + JWT | 10.3 | auth, gateway | Active |
| Password hashing | Spring Security Crypto | Boot-managed | auth | Active, BCrypt cost 12 |
| TOTP primitive | Bouncy Castle | 1.80 | auth | Active, HMAC-SHA1 |
| Events | Spring Kafka | Boot-managed | accounts, transfers | Active demo producer/consumer |
| State dependency | Spring State Machine | 4.0.0 | transfers | Declared; not configured yet |
| Reactive persistence | Spring Data R2DBC + r2dbc-postgresql | Boot-managed | accounts | Prepared only |
| Migrations | Liquibase + PostgreSQL JDBC | Boot-managed | auth, accounts, transfers | Changelogs present |
| Tests | JUnit Jupiter via spring-boot-starter-test | Boot-managed | domain | Domain tests |
| Native DNS | Netty macOS resolver | Boot-managed, `osx-aarch_64` | gateway | Apple Silicon runtime support |

## Local infrastructure

| Service | Image | Host port | Purpose |
| --- | --- | ---: | --- |
| PostgreSQL | `postgres:17-alpine` | 5432 | Future persistence and Liquibase database |
| Apache Kafka | `apache/kafka:4.0.2` | 9092 | KRaft-mode local broker |
| Redis | `redis:7-alpine` | 6379 | Reserved for gateway rate limiting |
| Oracle XE | `gvenzl/oracle-xe:21-slim` | 1521 | Reserved for reports module |
| Zipkin | `openzipkin/zipkin` | 9411 | Reserved for tracing export |

No application containers are defined in Compose yet; applications run from Maven during local development.

## Version policy

Spring Boot controls versions for its managed dependencies. Versions are pinned explicitly only where the project needs a direct contract: Spring Cloud BOM, Nimbus JOSE, Bouncy Castle, State Machine, and the Docker images. Upgrade the Spring Boot parent and Spring Cloud BOM together after checking their compatibility matrix.

## Security notes

- No private keys, passwords or `.env` values are stored in the repository.
- JWE uses `RSA-OAEP-256` and `A256GCM`; inner JWS uses `RS256`.
- TOTP secrets currently live only in demo memory. Before persistence they require envelope encryption with a managed KMS.
- The macOS Netty resolver is platform-specific. Linux deployments use Netty's regular resolver and do not need the classifier artifact.
