package com.vault.auth.repository;

import com.vault.auth.domain.AuthUser;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AuthUserRepository extends ReactiveCrudRepository<AuthUser, UUID> {
  Mono<AuthUser> findByEmail(String email);
  Mono<Boolean> existsByEmail(String email);
}
