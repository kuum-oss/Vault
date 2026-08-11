package com.vault.transfers;

import com.vault.domain.Transfer;
import com.vault.domain.TransferEvent;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@SpringBootApplication public class TransfersApplication { public static void main(String[] args) { SpringApplication.run(TransfersApplication.class, args); } }
@RestController @RequestMapping("/transfers") class TransfersController {
  private final ConcurrentHashMap<UUID, Transfer> transfers = new ConcurrentHashMap<>();
  private final KafkaTemplate<String, TransferEvent> kafka;
  TransfersController(KafkaTemplate<String, TransferEvent> kafka) { this.kafka = kafka; }
  @PostMapping Mono<Transfer> create(@RequestBody CreateTransfer r) { Transfer t = new Transfer(UUID.randomUUID(), r.fromAccountId(), r.toAccountId(), r.amount(), r.currency(), Transfer.State.DRAFT, 0); transfers.put(t.transferId(), t); return Mono.just(t); }
  @PostMapping("/{id}/submit") Mono<Transfer> submit(@PathVariable UUID id) { return apply(id, Transfer.Event.SUBMIT); }
  @GetMapping("/{id}") Mono<Transfer> get(@PathVariable UUID id) { return Mono.justOrEmpty(transfers.get(id)); }
  private Mono<Transfer> apply(UUID id, Transfer.Event event) { Transfer t = transfers.get(id); if (t == null) return Mono.empty(); Transfer next = t.transition(event); transfers.put(id, next); publish(next); return Mono.just(next); }
  private void publish(Transfer transfer) { String topic = switch (transfer.state()) { case INITIATED -> "vault.transfer.initiated"; case SETTLED -> "vault.transfer.settled"; case FAILED -> "vault.transfer.failed"; default -> null; }; if (topic != null) kafka.send(topic, transfer.transferId().toString(), TransferEvent.of(transfer)); }
  record CreateTransfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String currency) {}
}
