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
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import com.vault.auth.service.AuthUserService;
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
  private final AuthUserService users;
  AuthController(TokenService tokens, AuthUserService users) { this.tokens = tokens; this.users = users; }
  @PostMapping("/register") Mono<RegisterResponse> register(@RequestBody RegisterRequest body) { return users.register(body.email(), body.password()).map(result -> new RegisterResponse(result.userId(), result.totpSecret(), result.otpauthUri())); }
  @PostMapping("/login") Mono<LoginResponse> login(@RequestBody LoginRequest body) { return users.verifyPassword(body.email(), body.password()).map(user -> new LoginResponse(true)); }
  @PostMapping("/verify-totp") Mono<TokenResponse> verify(@RequestBody TotpRequest body) throws Exception {
    return users.verifyTotp(body.email(), body.code()).map(user -> { try { return new TokenResponse(tokens.issue(user.id(), "CUSTOMER"), "Bearer", 900); } catch (Exception e) { throw new IllegalStateException(e); } });
  }
  @PostMapping("/refresh") Mono<TokenResponse> refresh(@RequestHeader("Authorization") String auth) throws Exception { return Mono.just(new TokenResponse(tokens.refresh(auth.substring(7)), "Bearer", 900)); }
  record RegisterRequest(String email, String password) {} record LoginRequest(String email, String password) {} record TotpRequest(String email, String code) {} record RegisterResponse(java.util.UUID userId, String totpSecret, String otpauthUri) {} record LoginResponse(boolean totpChallenge) {} record TokenResponse(String accessToken, String tokenType, int expiresIn) {}
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
