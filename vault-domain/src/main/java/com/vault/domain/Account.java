package com.vault.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Account(UUID accountId, UUID ownerId, String currency, BigDecimal balance, Status status) {
  public enum Status { ACTIVE, FROZEN, CLOSED }
  public Account freeze() { return new Account(accountId, ownerId, currency, balance, Status.FROZEN); }
}
