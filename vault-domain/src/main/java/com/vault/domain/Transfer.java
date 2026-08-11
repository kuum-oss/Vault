package com.vault.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Transfer(UUID transferId, UUID fromAccountId, UUID toAccountId, BigDecimal amount,
                       String currency, State state, int attempts) {
  public enum State { DRAFT, INITIATED, PROCESSING, SETTLED, FAILED, REVERSED }
  public enum Event { SUBMIT, RESERVE, COMMIT, FAIL, RETRY, REVERSE }
  public Transfer transition(Event event) {
    State next = switch (state) {
      case DRAFT -> event == Event.SUBMIT ? State.INITIATED : state;
      case INITIATED -> event == Event.RESERVE ? State.PROCESSING : state;
      case PROCESSING -> event == Event.COMMIT ? State.SETTLED : event == Event.FAIL ? State.FAILED : state;
      case FAILED -> event == Event.RETRY && attempts < 3 ? State.INITIATED : state;
      case SETTLED -> event == Event.REVERSE ? State.REVERSED : state;
      case REVERSED -> state;
    };
    return next == state ? this : new Transfer(transferId, fromAccountId, toAccountId, amount, currency, next,
        event == Event.RETRY ? attempts + 1 : attempts);
  }
}
