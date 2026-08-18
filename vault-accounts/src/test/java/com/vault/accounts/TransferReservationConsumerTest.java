package com.vault.accounts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vault.domain.Transfer;
import com.vault.domain.TransferEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;

class TransferReservationConsumerTest {

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
  private final AccountAuditRepository auditRepository = mock(AccountAuditRepository.class);
  private TransferReservationConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new TransferReservationConsumer(kafka, auditRepository);
  }

  @Test
  void processesNewEventAndSendsKafkaMessage() {
    UUID eventId = UUID.randomUUID();
    UUID transferId = UUID.randomUUID();
    UUID fromAccountId = UUID.randomUUID();
    UUID toAccountId = UUID.randomUUID();
    TransferEvent event = new TransferEvent(
        eventId, transferId, fromAccountId, toAccountId, BigDecimal.valueOf(100), "USD", Transfer.State.INITIATED, Instant.now()
    );

    when(auditRepository.existsByEventId(eventId)).thenReturn(Mono.just(false));
    when(auditRepository.save(any(AccountAuditEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    consumer.reserve(event);

    verify(auditRepository, times(1)).save(any(AccountAuditEntity.class));
    verify(kafka, times(1)).send(eq("vault.transfer.reserved"), eq(transferId.toString()), eq(eventId.toString()));
  }

  @Test
  void skipsAlreadyProcessedEvent() {
    UUID eventId = UUID.randomUUID();
    UUID transferId = UUID.randomUUID();
    TransferEvent event = new TransferEvent(
        eventId, transferId, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(50), "USD", Transfer.State.INITIATED, Instant.now()
    );

    when(auditRepository.existsByEventId(eventId)).thenReturn(Mono.just(true));

    consumer.reserve(event);

    verify(auditRepository, never()).save(any());
    verify(kafka, never()).send(any(), any(), any());
  }
}
