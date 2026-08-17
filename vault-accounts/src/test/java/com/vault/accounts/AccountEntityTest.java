package com.vault.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vault.domain.Account;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountEntityTest {

  @Test
  void fromAccountMapsAllSchemaFieldsAndSetsNullVersionForInsert() {
    Account account = new Account(UUID.randomUUID(), UUID.randomUUID(), "USD", BigDecimal.ZERO, Account.Status.ACTIVE);
    AccountEntity entity = AccountEntity.from(account);

    assertEquals(account.accountId(), entity.accountId());
    assertEquals(account.ownerId(), entity.ownerId());
    assertEquals("USD", entity.currency());
    assertEquals(BigDecimal.ZERO, entity.balance());
    assertEquals(BigDecimal.ZERO, entity.reserved());
    assertEquals("ACTIVE", entity.status());
    assertNull(entity.version(), "New entity version must be null so Spring Data R2DBC performs INSERT instead of UPDATE");
    assertNotNull(entity.createdAt());
    assertNotNull(entity.updatedAt());
  }

  @Test
  void freezePreservesVersionAndReserved() {
    UUID id = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
    OffsetDateTime updatedAt = OffsetDateTime.now().minusHours(2);
    AccountEntity entity = new AccountEntity(
        id, ownerId, "EUR", BigDecimal.TEN, new BigDecimal("5.0000"), "ACTIVE", 2L, createdAt, updatedAt
    );

    AccountEntity frozen = entity.freeze();
    assertEquals("FROZEN", frozen.status());
    assertEquals(2L, frozen.version());
    assertEquals(new BigDecimal("5.0000"), frozen.reserved());
    assertEquals(createdAt, frozen.createdAt());
    assertNotNull(frozen.updatedAt());
  }

  @Test
  void toDomainMapping() {
    UUID id = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();
    AccountEntity entity = new AccountEntity(
        id, ownerId, "EUR", BigDecimal.TEN, BigDecimal.ZERO, "ACTIVE", 1L, now, now
    );

    Account domain = entity.toDomain();
    assertEquals(id, domain.accountId());
    assertEquals(ownerId, domain.ownerId());
    assertEquals("EUR", domain.currency());
    assertEquals(BigDecimal.TEN, domain.balance());
    assertEquals(Account.Status.ACTIVE, domain.status());
  }
}
