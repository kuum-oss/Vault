package com.vault.accounts;

import com.vault.domain.Account;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

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
  static AccountEntity from(Account account) {
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

  AccountEntity freeze() {
    return new AccountEntity(
        accountId,
        ownerId,
        currency,
        balance,
        reserved,
        Account.Status.FROZEN.name(),
        version,
        createdAt,
        OffsetDateTime.now()
    );
  }

  Account toDomain() {
    return new Account(accountId, ownerId, currency, balance, Account.Status.valueOf(status));
  }
}
