package com.vault.accounts;

import com.vault.domain.Account;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("accounts")
public record AccountEntity(@Id UUID accountId, UUID ownerId, String currency, BigDecimal balance, String status) {
  static AccountEntity from(Account account) { return new AccountEntity(account.accountId(), account.ownerId(), account.currency(), account.balance(), account.status().name()); }
  Account toDomain() { return new Account(accountId, ownerId, currency, balance, Account.Status.valueOf(status)); }
}
