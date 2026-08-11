package com.vault.auth.service;

import com.vault.auth.domain.AuthUser;
import com.vault.auth.kms.TotpEnvelope;
import com.vault.auth.kms.VaultKmsAdapter;
import com.vault.auth.repository.AuthUserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthUserService {
  private final AuthUserRepository users; private final VaultKmsAdapter kms; private final PasswordEncoder passwords; private final TotpService totp;
  public AuthUserService(AuthUserRepository users, VaultKmsAdapter kms, PasswordEncoder passwords, TotpService totp) { this.users = users; this.kms = kms; this.passwords = passwords; this.totp = totp; }
  public Mono<RegisterResult> register(String email, String password) { return users.existsByEmail(email).flatMap(exists -> { if (exists) return Mono.error(new IllegalArgumentException("email already registered")); String seed = totp.generateSecret(); TotpEnvelope envelope = kms.encrypt(seed); return users.save(AuthUser.create(email, passwords.encode(password), envelope.ciphertext(), envelope.nonce(), envelope.encryptedDek(), envelope.keyVersion())).map(user -> new RegisterResult(user.id(), seed, totp.uri(email, seed))); }); }
  public Mono<AuthUser> verifyPassword(String email, String password) { return users.findByEmail(email).filter(user -> passwords.matches(password, user.passwordHash())).switchIfEmpty(Mono.error(new IllegalArgumentException("invalid credentials"))); }
  public Mono<AuthUser> verifyTotp(String email, String code) { return users.findByEmail(email).filter(user -> !user.accountFrozen()).filter(user -> totp.verify(kms.decrypt(user.totpCiphertext(), user.totpNonce(), user.totpEncryptedDek()), code)).switchIfEmpty(Mono.error(new IllegalArgumentException("invalid TOTP code"))); }
  public record RegisterResult(UUID userId, String totpSecret, String otpauthUri) {}
}
