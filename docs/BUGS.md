# Vault — Аудит дефектов и руководство по исправлению

Дата: 2026-08-16  
Ревизия: полная проверка всех модулей (`vault-accounts`, `vault-transfers`, `vault-gateway`, `vault-auth`, `vault-reports`, `vault-domain`)

---

## Сводка дефектов

| № | Критичность | Статус | Модуль | Файл | Дефект |
|---|---|---|---|---|---|
| 1 | 🔴 Критический | ✅ **Исправлен** | `vault-accounts` | [`AccountEntity.java`](file:///Users/dimagordeev/IdeaProjects/ser/vault-accounts/src/main/java/com/vault/accounts/AccountEntity.java#L10) | Несовпадение имени PK-столбца: `id` в БД vs `account_id` в entity |
| 2 | 🔴 Критический | ✅ **Исправлен** | `vault-accounts` | [`AccountsApplication.java`](file:///Users/dimagordeev/IdeaProjects/ser/vault-accounts/src/main/java/com/vault/accounts/AccountsApplication.java#L20) | `save()` выполняет UPDATE вместо INSERT при создании нового счёта |
| 3 | 🟡 Серьёзный | ✅ **Исправлен** | `vault-accounts` | [`AccountEntity.java`](file:///Users/dimagordeev/IdeaProjects/ser/vault-accounts/src/main/java/com/vault/accounts/AccountEntity.java#L10) | Entity не отображает 4 обязательных столбца схемы (`reserved`, `version`, `created_at`, `updated_at`) |
| 4 | 🟡 Серьёзный | ✅ **Исправлен** | `vault-transfers` | [`pom.xml`](file:///Users/dimagordeev/IdeaProjects/ser/vault-transfers/pom.xml) | Отсутствует `spring-boot-starter-jdbc` — Liquibase не создаёт таблицы |
| 5 | 🟡 Серьёзный | ⏳ Открыт | `vault-accounts` | [`AccountsApplication.java`](file:///Users/dimagordeev/IdeaProjects/ser/vault-accounts/src/main/java/com/vault/accounts/AccountsApplication.java#L29) | Дедупликация событий в памяти теряется при рестарте |
| 6 | 🟠 Умеренный | ⏳ Открыт | `vault-gateway` | [`JweAuthenticationFilter.java`](file:///Users/dimagordeev/IdeaProjects/ser/vault-gateway/src/main/java/com/vault/gateway/JweAuthenticationFilter.java) | Фильтр аутентификации отключён по умолчанию без предупреждения |
| 7 | 🟠 Умеренный | ⏳ Открыт | `vault-transfers` | [`TransfersApplication.java`](file:///Users/dimagordeev/IdeaProjects/ser/vault-transfers/src/main/java/com/vault/transfers/TransfersApplication.java#L22) | Неатомарное чтение-запись в `ConcurrentHashMap` |

---

## 🔴 Дефект 1 — Несовпадение имени первичного ключа `AccountEntity` [✅ ИСПРАВЛЕН]

**Модуль**: vault-accounts  
**Файл**: [`AccountEntity.java`](file:///Users/dimagordeev/IdeaProjects/ser/vault-accounts/src/main/java/com/vault/accounts/AccountEntity.java#L10)  
**Связанная схема**: [`master.yaml`](file:///Users/dimagordeev/IdeaProjects/ser/vault-accounts/src/main/resources/db/changelog/master.yaml#L9)  
**Статус**: ✅ **Исправлен** — добавлена аннотация `@Column("id")` для поля `@Id UUID accountId`.

### Суть проблемы
Liquibase-changelog определяет первичный ключ таблицы `accounts` как столбец **`id`**:
```yaml
- column: { name: id, type: UUID, constraints: { primaryKey: true, nullable: false } }
```
Java-entity объявляет поле `@Id UUID accountId`:
```java
@Table("accounts")
public record AccountEntity(@Id UUID accountId, UUID ownerId, ...) { ... }
```
Spring Data R2DBC применяет стратегию именования `NamingStrategy`, преобразуя `accountId` в столбец **`account_id`**.

### Последствия
Любой вызов репозитория (`findAll()`, `findById()`, `save()`) генерирует SQL с несуществующим столбцом `account_id`, вызывая ошибку PostgreSQL:
```
column "account_id" of relation "accounts" does not exist
```
Весь CRUD модуля `vault-accounts` не работает при подключении к БД.

### Исправление
```diff
 @Table("accounts")
-public record AccountEntity(@Id UUID accountId, UUID ownerId, String currency,
+public record AccountEntity(@Id @Column("id") UUID accountId, UUID ownerId, String currency,
                             BigDecimal balance, String status) {
```

---

## 🔴 Дефект 2 — `save()` выполняет UPDATE вместо INSERT для новых счетов [✅ ИСПРАВЛЕН]

**Модуль**: vault-accounts  
**Файл**: [`AccountsApplication.java`](file:///Users/dimagordeev/IdeaProjects/ser/vault-accounts/src/main/java/com/vault/accounts/AccountsApplication.java#L20)

### Суть проблемы
Контроллер создаёт аккаунт с предварительно сгенерированным UUID:
```java
Account a = new Account(UUID.randomUUID(), request.ownerId(), request.currency(),
                        BigDecimal.ZERO, Account.Status.ACTIVE);
return accounts.save(AccountEntity.from(a));
```
Spring Data R2DBC определяет новизну сущности по значению `@Id`:
- `@Id == null` → **INSERT**
- `@Id != null` → **UPDATE**

Так как `UUID.randomUUID()` не `null`, `save()` генерирует `UPDATE ... WHERE id = ?`. Строки с таким UUID в базе нет, обновляется 0 строк.

### Последствия
Новые счета не сохраняются в базу данных. Выбрасывается исключение либо запрос завершается без создания записи.

### Исправление
Добавить поле `@Version Long version` (при `null` значении версии Spring Data выполняет INSERT):
```diff
 @Table("accounts")
 public record AccountEntity(
     @Id @Column("id") UUID accountId,
     UUID ownerId,
     String currency,
     BigDecimal balance,
     String status,
+    @Version Long version
 ) { ... }
```

---

## 🟡 Дефект 3 — Entity не отображает 4 обязательных столбца схемы [✅ ИСПРАВЛЕН]

**Модуль**: vault-accounts  
**Файл**: [`AccountEntity.java`](file:///Users/dimagordeev/IdeaProjects/ser/vault-accounts/src/main/java/com/vault/accounts/AccountEntity.java#L10)

### Суть проблемы
Таблица `accounts` в БД содержит 9 колонок, а `AccountEntity` объявляет только 5:
- `reserved` (`DECIMAL(19,4)`, default 0)
- `version` (`BIGINT`, default 0)
- `created_at` (`TIMESTAMP WITH TIME ZONE`)
- `updated_at` (`TIMESTAMP WITH TIME ZONE`)

### Последствия
1. Нет оптимистической блокировки (`@Version`).
2. Невозможно читать или отслеживать зарезервированные средства (`reserved`).
3. Теряются временные метки аудита (`created_at`, `updated_at`).

### Исправление
Обновить `AccountEntity.java`:
```java
@Table("accounts")
public record AccountEntity(
    @Id @Column("id") UUID accountId,
    UUID ownerId,
    String currency,
    BigDecimal balance,
    BigDecimal reserved,
    String status,
    @Version Long version,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static AccountEntity from(Account account) {
        return new AccountEntity(
            account.accountId(),
            account.ownerId(),
            account.currency(),
            account.balance(),
            BigDecimal.ZERO,
            account.status().name(),
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now()
        );
    }

    public Account toDomain() {
        return new Account(accountId, ownerId, currency, balance, Account.Status.valueOf(status));
    }
}
```

---

## 🟡 Дефект 4 — Отсутствует `spring-boot-starter-jdbc` в `vault-transfers` [✅ ИСПРАВЛЕН]

**Модуль**: vault-transfers  
**Файл**: [`pom.xml`](file:///Users/dimagordeev/IdeaProjects/ser/vault-transfers/pom.xml)

### Суть проблемы
`vault-transfers/pom.xml` содержит `liquibase-core` и JDBC-драйвер PostgreSQL, но не содержит `spring-boot-starter-jdbc`.
Без него `DataSourceAutoConfiguration` не создаёт бин `DataSource`, и Liquibase не запускает миграции при старте приложения.

### Последствия
Таблицы `transfers` и `outbox_events` не создаются в базе данных PostgreSQL.

### Исправление
Добавить зависимость в [`vault-transfers/pom.xml`](file:///Users/dimagordeev/IdeaProjects/ser/vault-transfers/pom.xml):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

---

## 🟡 Дефект 5 — Дедупликация событий в памяти теряется при рестарте

**Модуль**: vault-accounts  
**Файл**: [`AccountsApplication.java`](file:///Users/dimagordeev/IdeaProjects/ser/vault-accounts/src/main/java/com/vault/accounts/AccountsApplication.java#L29-L35)

### Суть проблемы
Дедупликация входящих Kafka-событий реализована через `ConcurrentHashMap.newKeySet()` в оперативной памяти:
```java
private final java.util.Set<UUID> processed = ConcurrentHashMap.newKeySet();
```
При перезапуске сервиса кэш очищается, что приводит к повторной обработке сообщений при перебалансировке Kafka-консьюмера.

### Последствия
Повторная отправка событий и потенциальное дублирование финансовых транзакций при рестартах.

### Исправление
Использовать персистентный Transactional Inbox либо проверку уникальности `event_id` через таблицу `account_audit` (где уже настроен уникальный индекс `uq_account_audit_event`).

---

## 🟠 Дефект 6 — Фильтр аутентификации отключён по умолчанию без предупреждения

**Модуль**: vault-gateway  
**Файл**: [`JweAuthenticationFilter.java`](file:///Users/dimagordeev/IdeaProjects/ser/vault-gateway/src/main/java/com/vault/gateway/JweAuthenticationFilter.java)

### Суть проблемы
Если ключи `VAULT_RSA_PRIVATE_PEM` / `VAULT_RSA_PUBLIC_PEM` пусты, шлюз переходит в режим обхода аутентификации (`privateKey == null`), пропуская все запросы к защищённым сервисам без проверки токена.

### Последствия
Случайный запуск в production-окружении без установленных переменных окружения сделает API полностью публичным без генерации предупреждений или аварийного завершения.

### Исправление
1. Добавить явный лог уровня WARN при старте.
2. В non-dev профилях выбрасывать `IllegalStateException` при отсутствии RSA-ключей.

---

## 🟠 Дефект 7 — Неатомарное чтение-запись в `ConcurrentHashMap`

**Модуль**: vault-transfers  
**Файл**: [`TransfersApplication.java`](file:///Users/dimagordeev/IdeaProjects/ser/vault-transfers/src/main/java/com/vault/transfers/TransfersApplication.java#L22)

### Суть проблемы
```java
Transfer t = transfers.get(id);
if (t == null) return Mono.empty();
Transfer next = t.transition(event);
transfers.put(id, next);
```
Операции `get()` и `put()` разнесены во времени и не атомарны. Конкурентные запросы к одному переводу приводят к потере обновлений (lost update).

### Исправление
Использовать атомарный `computeIfPresent()`:
```diff
-Transfer t = transfers.get(id);
-if (t == null) return Mono.empty();
-Transfer next = t.transition(event);
-transfers.put(id, next);
-publish(next);
-return Mono.just(next);
+Transfer next = transfers.computeIfPresent(id, (k, current) -> current.transition(event));
+if (next == null) return Mono.empty();
+publish(next);
+return Mono.just(next);
```
*(При реализации персистентного R2DBC репозитория эта проблема решается с помощью оптимистической блокировки `@Version`).*

---

## Чеклист для устранения

- [x] **Шаг 1.1**: Исправить маппинг первичного ключа в `AccountEntity.java` (добавить `@Column("id")` — **Дефект 1**).
- [x] **Шаг 1.2**: Добавить `@Version` и недостающие поля (`reserved`, `version`, `created_at`, `updated_at`) в `AccountEntity.java` (**Дефекты 2, 3**).
- [x] **Шаг 2**: Добавить `spring-boot-starter-jdbc` в `vault-transfers/pom.xml` (**Дефект 4**).
- [ ] **Шаг 3**: Добавить логирование/fail-fast валидацию ключей в `JweAuthenticationFilter.java` (**Дефект 6**).
- [ ] **Шаг 4**: Применить `computeIfPresent` в `TransfersController` до подключения постоянной БД (**Дефект 7**).
- [ ] **Шаг 5**: Реализовать персистентный Transactional Outbox/Inbox для надежной дедупликации Kafka-событий (**Дефект 5**).
