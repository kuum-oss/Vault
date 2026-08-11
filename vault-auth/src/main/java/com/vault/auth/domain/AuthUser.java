package com.vault.auth.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("auth_users")
public record AuthUser(@Id UUID id, String email, String passwordHash, String totpCiphertext,
                       String totpNonce, String totpEncryptedDek, int totpKeyVersion,
                       boolean accountFrozen, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
  public static AuthUser create(String email, String passwordHash, String ciphertext, String nonce, String encryptedDek, int keyVersion) {
    OffsetDateTime now = OffsetDateTime.now();
    return new AuthUser(null, email, passwordHash, ciphertext, nonce, encryptedDek, keyVersion, false, now, now);
  }
}
