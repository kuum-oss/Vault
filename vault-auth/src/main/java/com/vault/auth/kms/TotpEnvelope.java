package com.vault.auth.kms;

/** Persisted envelope; the plaintext TOTP seed is never a database value. */
public record TotpEnvelope(String ciphertext, String nonce, String encryptedDek, int keyVersion) {}
