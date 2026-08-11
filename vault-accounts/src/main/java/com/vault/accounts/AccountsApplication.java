package com.vault.accounts;

import com.vault.domain.Account;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication public class AccountsApplication { public static void main(String[] args) { SpringApplication.run(AccountsApplication.class, args); } }
@RestController @RequestMapping("/accounts") class AccountsController {
  private final ConcurrentHashMap<UUID, Account> accounts = new ConcurrentHashMap<>();
  @GetMapping Flux<Account> all() { return Flux.fromIterable(accounts.values()); }
  @PostMapping Mono<Account> open(@RequestBody OpenAccount request) { Account a = new Account(UUID.randomUUID(), request.ownerId(), request.currency(), BigDecimal.ZERO, Account.Status.ACTIVE); accounts.put(a.accountId(), a); return Mono.just(a); }
  @GetMapping("/{id}/balance") Mono<BigDecimal> balance(@PathVariable UUID id) { Account a = accounts.get(id); return a == null ? Mono.error(new IllegalArgumentException("account not found")) : Mono.just(a.balance()); }
  @PatchMapping("/{id}/freeze") Mono<Account> freeze(@PathVariable UUID id) { Account a = accounts.get(id); return a == null ? Mono.error(new IllegalArgumentException("account not found")) : Mono.just(accounts.computeIfPresent(id, (k,v) -> v.freeze())); }
  record OpenAccount(UUID ownerId, String currency) {}
}
