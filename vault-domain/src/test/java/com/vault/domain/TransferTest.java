package com.vault.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferTest {
  @Test void followsHappyPath() {
    Transfer t = new Transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, "EUR", Transfer.State.DRAFT, 0);
    assertEquals(Transfer.State.SETTLED, t.transition(Transfer.Event.SUBMIT).transition(Transfer.Event.RESERVE).transition(Transfer.Event.COMMIT).state());
  }
  @Test void retryIsLimited() {
    Transfer t = new Transfer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, "EUR", Transfer.State.FAILED, 3);
    assertEquals(Transfer.State.FAILED, t.transition(Transfer.Event.RETRY).state());
  }
}
