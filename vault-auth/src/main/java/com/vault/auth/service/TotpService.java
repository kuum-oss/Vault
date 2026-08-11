package com.vault.auth.service;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

@Service
public class TotpService {
  public String generateSecret() { byte[] bytes = new byte[20]; new SecureRandom().nextBytes(bytes); return Base64.getEncoder().withoutPadding().encodeToString(bytes); }
  public boolean verify(String secret, String code) { long period = Instant.now().getEpochSecond() / 30; for (long offset = -1; offset <= 1; offset++) if (code.equals(code(secret, period + offset))) return true; return false; }
  public String uri(String email, String secret) { return "otpauth://totp/Vault:" + email + "?secret=" + secret + "&issuer=Vault&algorithm=SHA1&digits=6&period=30"; }
  private String code(String secret, long counter) { try { Mac mac = Mac.getInstance("HmacSHA1", new BouncyCastleProvider()); mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA1")); byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array()); int offset = hash[hash.length - 1] & 0xf; int value = ((hash[offset] & 0x7f) << 24 | (hash[offset + 1] & 0xff) << 16 | (hash[offset + 2] & 0xff) << 8 | (hash[offset + 3] & 0xff)) % 1_000_000; return "%06d".formatted(value); } catch (Exception e) { throw new IllegalStateException(e); } }
}
