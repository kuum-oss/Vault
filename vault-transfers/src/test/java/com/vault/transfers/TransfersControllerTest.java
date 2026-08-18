package com.vault.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.vault.domain.Transfer;
import com.vault.domain.TransferEvent;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class TransfersControllerTest {

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, TransferEvent> kafka = mock(KafkaTemplate.class);
  private TransfersController controller;

  @BeforeEach
  void setUp() {
    controller = new TransfersController(kafka);
  }

  @Test
  void createsAndSubmitsTransferAtomically() {
    TransfersController.CreateTransfer request = new TransfersController.CreateTransfer(
        UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(250), "EUR"
    );

    Transfer created = controller.create(request).block();
    assertNotNull(created);
    assertEquals(Transfer.State.DRAFT, created.state());

    Transfer submitted = controller.submit(created.transferId()).block();
    assertNotNull(submitted);
    assertEquals(Transfer.State.INITIATED, submitted.state());

    verify(kafka).send(eq("vault.transfer.initiated"), eq(created.transferId().toString()), any(TransferEvent.class));
  }
}
