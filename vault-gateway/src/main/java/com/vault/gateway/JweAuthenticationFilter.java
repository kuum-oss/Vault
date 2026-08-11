package com.vault.gateway;

import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
class JweAuthenticationFilter implements GlobalFilter, Ordered {
  private final RSAPrivateKey privateKey; private final RSAPublicKey publicKey;
  JweAuthenticationFilter(@Value("${vault.security.rsa-private-pem:}") String privatePem, @Value("${vault.security.rsa-public-pem:}") String publicPem) throws Exception {
    if (privatePem.isBlank() || publicPem.isBlank()) { privateKey = null; publicKey = null; return; }
    KeyFactory f = KeyFactory.getInstance("RSA"); privateKey = (RSAPrivateKey) f.generatePrivate(new PKCS8EncodedKeySpec(decode(privatePem))); publicKey = (RSAPublicKey) f.generatePublic(new X509EncodedKeySpec(decode(publicPem)));
  }
  @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value(); if (path.startsWith("/auth/") || path.startsWith("/actuator/") || privateKey == null) return chain.filter(exchange);
    String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION); if (auth == null || !auth.startsWith("Bearer ")) return deny(exchange);
    try { JWEObject jwe = JWEObject.parse(auth.substring(7)); jwe.decrypt(new RSADecrypter(privateKey)); SignedJWT jws = jwe.getPayload().toSignedJWT(); if (jws == null || !jws.verify(new RSASSAVerifier(publicKey))) return deny(exchange); var claims = jws.getJWTClaimsSet(); if (claims.getExpirationTime().before(new java.util.Date())) return deny(exchange); String userId = claims.getSubject(); String role = claims.getStringClaim("role"); return chain.filter(exchange.mutate().request(r -> r.headers(h -> { h.remove(HttpHeaders.AUTHORIZATION); h.set("X-User-Id", userId); h.set("X-User-Role", role); })).build()); } catch (Exception e) { return deny(exchange); }
  }
  private Mono<Void> deny(ServerWebExchange e) { e.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); return e.getResponse().setComplete(); }
  private static byte[] decode(String pem) { return Base64.getDecoder().decode(pem.replaceAll("-----BEGIN [^-]+-----|-----END [^-]+-----|\\s", "")); }
  @Override public int getOrder() { return -100; }
}
