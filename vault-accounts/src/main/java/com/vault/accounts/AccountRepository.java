package com.vault.accounts;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

interface AccountRepository extends ReactiveCrudRepository<AccountEntity, UUID> {}
