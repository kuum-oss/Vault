package com.vault.accounts;

import com.vault.domain.Account;
import com.vault.domain.TransferEvent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication public class AccountsApplication { public static void main(String[] args) { SpringApplication.run(AccountsApplication.class, args); } }
@RestController @RequestMapping("/accounts") class AccountsController {
  private final AccountRepository accounts;
  AccountsController(AccountRepository accounts) { this.accounts = accounts; }
  @GetMapping Flux<Account> all() { return accounts.findAll().map(AccountEntity::toDomain); }
  @PostMapping Mono<Account> open(@RequestBody OpenAccount request) { Account a = new Account(UUID.randomUUID(), request.ownerId(), request.currency(), BigDecimal.ZERO, Account.Status.ACTIVE); return accounts.save(AccountEntity.from(a)).map(AccountEntity::toDomain); }
  @GetMapping("/{id}/balance") Mono<BigDecimal> balance(@PathVariable UUID id) { return accounts.findById(id).map(AccountEntity::balance).switchIfEmpty(Mono.error(new IllegalArgumentException("account not found"))); }
  @PatchMapping("/{id}/freeze") Mono<Account> freeze(@PathVariable UUID id) { return accounts.findById(id).map(AccountEntity::freeze).flatMap(accounts::save).map(AccountEntity::toDomain).switchIfEmpty(Mono.error(new IllegalArgumentException("account not found"))); }
  record OpenAccount(UUID ownerId, String currency) {}
}

@org.springframework.stereotype.Component
class TransferReservationConsumer {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TransferReservationConsumer.class);
  private final KafkaTemplate<String, String> kafka;
  private final AccountAuditRepository auditRepository;

  TransferReservationConsumer(KafkaTemplate<String, String> kafka, AccountAuditRepository auditRepository) {
    this.kafka = kafka;
    this.auditRepository = auditRepository;
  }

  @KafkaListener(topics = "vault.transfer.initiated", groupId = "vault-accounts")
  void reserve(TransferEvent event) {
    Boolean exists = auditRepository.existsByEventId(event.eventId()).block();
    if (Boolean.TRUE.equals(exists)) {
      log.debug("Event {} already processed, skipping", event.eventId());
      return;
    }

    AccountAuditEntity audit = new AccountAuditEntity(
        UUID.randomUUID(),
        event.fromAccountId(),
        "TRANSFER_RESERVED",
        null,
        event.amount() != null ? event.amount().negate() : null,
        null,
        event.eventId(),
        OffsetDateTime.now()
    );

    try {
      auditRepository.save(audit).block();
      kafka.send("vault.transfer.reserved", event.transferId().toString(), event.eventId().toString());
    } catch (DuplicateKeyException e) {
      log.debug("Event {} already processed concurrently, skipping", event.eventId());
    }
  }
}
