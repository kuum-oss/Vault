package com.vault.auth.kms;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultTransitContext;

/** Envelope encryption: Vault encrypts only a per-user DEK; AES-GCM encrypts the TOTP secret. */
public final class VaultKmsAdapter {
  private static final String KEY = "vault-totp-key";
  private final VaultOperations vault;
  public VaultKmsAdapter(VaultOperations vault) { this.vault = vault; }
  public TotpEnvelope encrypt(String plaintext) { return encrypt(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
  public String decrypt(String ciphertext, String nonce, String encryptedDek) { return new String(decrypt(new EncryptedSecret(Base64.getDecoder().decode(ciphertext), Base64.getDecoder().decode(nonce), encryptedDek, keyVersion(encryptedDek))), java.nio.charset.StandardCharsets.UTF_8); }
  private TotpEnvelope encrypt(byte[] plaintext) {
    try { byte[] dek = new byte[32], nonce = new byte[12]; SecureRandom random = new SecureRandom(); random.nextBytes(dek); random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(dek, "AES"), new GCMParameterSpec(128, nonce));
      String encryptedDek = vault.opsForTransit().encrypt(KEY, dek, VaultTransitContext.empty());
      return new TotpEnvelope(Base64.getEncoder().encodeToString(cipher.doFinal(plaintext)), Base64.getEncoder().encodeToString(nonce), encryptedDek, keyVersion(encryptedDek));
    } catch (Exception e) { throw new IllegalStateException("Vault Transit encryption failed", e); }
  }
  private byte[] decrypt(EncryptedSecret secret) {
    try { byte[] dek = vault.opsForTransit().decrypt(KEY, secret.encryptedDek(), VaultTransitContext.empty()); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(dek, "AES"), new GCMParameterSpec(128, secret.nonce())); return cipher.doFinal(secret.ciphertext()); } catch (Exception e) { throw new IllegalStateException("Vault Transit decryption failed", e); }
  }
  private static int keyVersion(String ciphertext) { String[] parts = ciphertext.split(":"); return parts.length > 1 && parts[1].startsWith("v") ? Integer.parseInt(parts[1].substring(1)) : 0; }
  public record EncryptedSecret(byte[] ciphertext, byte[] nonce, String encryptedDek, int keyVersion) {}
}
