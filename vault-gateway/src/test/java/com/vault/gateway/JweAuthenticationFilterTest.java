package com.vault.gateway;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JweAuthenticationFilterTest {

  @Test
  void permitsEmptyKeysInDevProfile() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("dev");

    assertDoesNotThrow(() -> new JweAuthenticationFilter("", "", env));
  }

  @Test
  void permitsEmptyKeysWhenNoActiveProfileDefault() {
    MockEnvironment env = new MockEnvironment();

    assertDoesNotThrow(() -> new JweAuthenticationFilter("", "", env));
  }

  @Test
  void throwsInProductionWhenKeysAreMissing() {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("prod");

    assertThrows(IllegalStateException.class, () -> new JweAuthenticationFilter("", "", env));
  }
}
