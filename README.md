# Vault

Учебный fintech-проект на Java 25 и Spring Boot 3.5: реактивные API, Kafka-события переводов, State Machine, JWE/JWS auth, TOTP, gateway, discovery/config, PostgreSQL/R2DBC, Oracle reports и observability.

## Быстрый старт

```bash
docker compose -f docker/docker-compose.yml up -d
mvn clean verify
mvn -pl vault-gateway spring-boot:run
```

Сервисы запускаются на `8080` (gateway), `8081` (auth), `8082` (accounts), `8083` (transfers), `8084` (reports). Проверка: `curl http://localhost:8080/actuator/health`.

Сейчас auth использует dev-токен для локального сквозного запуска. Следующий production-шаг — вынести RSA-ключи и секрет TOTP в Secret Manager, подключить Liquibase changelog и Kafka consumers.
