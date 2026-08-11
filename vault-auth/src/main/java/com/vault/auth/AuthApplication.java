package com.vault.auth;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class AuthApplication {
  public static void main(String[] args) { SpringApplication.run(AuthApplication.class, args); }
}

@RestController
@RequestMapping("/auth")
class AuthController {
  @PostMapping("/register") Mono<Map<String, Object>> register(@RequestBody Map<String, String> body) {
    return Mono.just(Map.of("userId", UUID.randomUUID(), "email", body.getOrDefault("email", ""), "totpEnabled", true));
  }
  @PostMapping("/login") Mono<Map<String, Object>> login() { return Mono.just(Map.of("totpChallenge", true, "challengeId", UUID.randomUUID())); }
  @PostMapping("/verify-totp") Mono<Map<String, Object>> verify(@RequestBody Map<String, String> body) {
    return Mono.just(Map.of("accessToken", "dev-jwe-token", "tokenType", "Bearer", "expiresAt", Instant.now().plusSeconds(900)));
  }
  @PostMapping("/refresh") Mono<Map<String, Object>> refresh() { return Mono.just(Map.of("accessToken", "dev-jwe-token", "tokenType", "Bearer")); }
}
