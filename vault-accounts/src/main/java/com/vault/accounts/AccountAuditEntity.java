package com.vault.accounts;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Table("account_audit")
public record AccountAuditEntity(
    @Id UUID id,
    UUID accountId,
    String action,
    UUID actorId,
    BigDecimal delta,
    BigDecimal balanceAfter,
    UUID eventId,
    OffsetDateTime createdAt
) implements Persistable<UUID> {

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return true;
  }
}
