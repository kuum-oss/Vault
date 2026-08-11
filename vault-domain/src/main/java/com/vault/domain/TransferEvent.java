package com.vault.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferEvent(UUID eventId, UUID transferId, UUID fromAccountId, UUID toAccountId,
                            BigDecimal amount, String currency, Transfer.State state, Instant occurredAt) {
  public static TransferEvent of(Transfer transfer) {
    return new TransferEvent(UUID.randomUUID(), transfer.transferId(), transfer.fromAccountId(), transfer.toAccountId(),
        transfer.amount(), transfer.currency(), transfer.state(), Instant.now());
  }
}
