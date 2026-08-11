package com.vault.auth;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class AuthApplication {
  public static void main(String[] args) { SpringApplication.run(AuthApplication.class, args); }
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
  @Bean TokenService tokenService(@Value("${vault.security.rsa-private-pem:}") String privatePem,
                                  @Value("${vault.security.rsa-public-pem:}") String publicPem,
                                  @Value("${spring.profiles.active:dev}") String profile) throws Exception {
    if (!privatePem.isBlank() && !publicPem.isBlank()) return TokenService.fromPem(privatePem, publicPem);
    if (!profile.contains("dev")) throw new IllegalStateException("RSA keys must be supplied through Secret Manager in non-dev profiles");
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(3072);
    KeyPair pair = generator.generateKeyPair(); return new TokenService((RSAPrivateKey) pair.getPrivate(), (RSAPublicKey) pair.getPublic());
  }
}

@RestController
@RequestMapping("/auth")
class AuthController {
  private final TokenService tokens;
  private final PasswordEncoder passwords;
  private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
  AuthController(TokenService tokens, PasswordEncoder passwords) { this.tokens = tokens; this.passwords = passwords; }
  @PostMapping("/register") Mono<Map<String, Object>> register(@RequestBody RegisterRequest body) {
    UUID id = UUID.randomUUID(); String secret = TotpService.newSecret();
    users.put(body.email(), new User(id, body.email(), passwords.encode(body.password()), secret));
    return Mono.just(Map.of("userId", id, "totpSecret", secret, "otpauthUri", TotpService.uri(body.email(), secret)));
  }
  @PostMapping("/login") Mono<Map<String, Object>> login(@RequestBody LoginRequest body) {
    User user = users.get(body.email());
    if (user == null || !passwords.matches(body.password(), user.password())) return Mono.error(new ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
    return Mono.just(Map.of("totpChallenge", true, "challengeId", user.id()));
  }
  @PostMapping("/verify-totp") Mono<TokenResponse> verify(@RequestBody TotpRequest body) throws Exception {
    User user = users.get(body.email());
    if (user == null || !TotpService.verify(user.totpSecret(), body.code(), Instant.now().getEpochSecond())) return Mono.error(new ApiException(HttpStatus.UNAUTHORIZED, "invalid TOTP code"));
    return Mono.just(new TokenResponse(tokens.issue(user.id(), "CUSTOMER"), "Bearer", 900));
  }
  @PostMapping("/refresh") Mono<TokenResponse> refresh(@RequestHeader("Authorization") String auth) throws Exception { return Mono.just(new TokenResponse(tokens.refresh(auth.substring(7)), "Bearer", 900)); }
  record RegisterRequest(String email, String password) {} record LoginRequest(String email, String password) {} record TotpRequest(String email, String code) {} record TokenResponse(String accessToken, String tokenType, int expiresIn) {} record User(UUID id, String email, String password, String totpSecret) {}
}

@ResponseStatus(HttpStatus.UNAUTHORIZED) class ApiException extends RuntimeException { ApiException(HttpStatus ignored, String message) { super(message); } }

final class TokenService {
  private final RSAPrivateKey privateKey; private final RSAPublicKey publicKey;
  TokenService(RSAPrivateKey privateKey, RSAPublicKey publicKey) { this.privateKey = privateKey; this.publicKey = publicKey; }
  String issue(UUID userId, String role) throws Exception {
    JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(userId.toString()).claim("role", role).issueTime(new java.util.Date()).expirationTime(java.util.Date.from(Instant.now().plusSeconds(900))).issuer("vault-auth").build();
    SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims); signed.sign(new RSASSASigner(privateKey));
    JWEObject encrypted = new JWEObject(new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM).contentType("JWT").build(), new com.nimbusds.jose.Payload(signed));
    encrypted.encrypt(new RSAEncrypter(publicKey)); return encrypted.serialize();
  }
  String refresh(String token) throws Exception { JWEObject encrypted = JWEObject.parse(token); encrypted.decrypt(new RSADecrypter(privateKey)); SignedJWT signed = encrypted.getPayload().toSignedJWT(); if (signed == null) throw new IllegalArgumentException("invalid token"); return issue(UUID.fromString(signed.getJWTClaimsSet().getSubject()), signed.getJWTClaimsSet().getStringClaim("role")); }
  static TokenService fromPem(String privatePem, String publicPem) throws Exception { KeyFactory factory = KeyFactory.getInstance("RSA"); return new TokenService((RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(decode(privatePem))), (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(decode(publicPem)))); }
  private static byte[] decode(String pem) { return Base64.getDecoder().decode(pem.replaceAll("-----BEGIN [^-]+-----|-----END [^-]+-----|\\s", "")); }
}

final class TotpService {
  private TotpService() {} static String newSecret() { byte[] bytes = new byte[20]; new java.security.SecureRandom().nextBytes(bytes); return Base64.getEncoder().withoutPadding().encodeToString(bytes); }
  static String uri(String email, String secret) { return "otpauth://totp/Vault:" + email + "?secret=" + secret + "&issuer=Vault&algorithm=SHA1&digits=6&period=30"; }
  static boolean verify(String secret, String code, long epochSeconds) { for (long offset = -1; offset <= 1; offset++) if (code.equals(code(secret, (epochSeconds / 30) + offset))) return true; return false; }
  private static String code(String secret, long counter) { try { byte[] msg = java.nio.ByteBuffer.allocate(8).putLong(counter).array(); Mac mac = Mac.getInstance("HmacSHA1", new BouncyCastleProvider()); mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA1")); byte[] hash = mac.doFinal(msg); int offset = hash[hash.length - 1] & 0xf; int value = ((hash[offset] & 0x7f) << 24 | (hash[offset + 1] & 0xff) << 16 | (hash[offset + 2] & 0xff) << 8 | (hash[offset + 3] & 0xff)) % 1_000_000; return "%06d".formatted(value); } catch (Exception e) { throw new IllegalStateException(e); } }
}
