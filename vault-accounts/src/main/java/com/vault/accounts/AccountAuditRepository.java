package com.vault.accounts;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AccountAuditRepository extends ReactiveCrudRepository<AccountAuditEntity, UUID> {
  Mono<Boolean> existsByEventId(UUID eventId);
}
