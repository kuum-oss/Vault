package com.vault.auth.kms;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.core.VaultOperations;
import org.springframework.context.annotation.Bean;

@Configuration
@Profile("vault")
class VaultTransitConfiguration extends AbstractVaultConfiguration {
  @Value("${vault.uri}") private URI uri;
  @Value("${vault.token}") private String token;
  @Override public VaultEndpoint vaultEndpoint() { return VaultEndpoint.from(uri); }
  @Override public ClientAuthentication clientAuthentication() { return new TokenAuthentication(token); }
  @Bean VaultKmsAdapter vaultKmsAdapter(VaultOperations vaultOperations) { return new VaultKmsAdapter(vaultOperations); }
}
