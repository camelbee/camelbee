package org.camelbee.security.routes.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.camelbee.security.routes.exception.InsufficientPrivilegesException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Unit tests for the static JWT authorization helper.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JwtAuthorizationUtilsTest {

  private DefaultCamelContext context;

  @BeforeAll
  void start() {
    context = new DefaultCamelContext();
    context.start();
  }

  @AfterAll
  void stop() {
    context.stop();
  }

  private Exchange exchangeWith(List<String> roles, List<String> scopes) {
    Exchange exchange = new DefaultExchange(context);
    if (roles != null) {
      exchange.setProperty("jwt.roles", roles);
    }
    if (scopes != null) {
      exchange.setProperty("jwt.scopes", scopes);
    }
    return exchange;
  }

  @Test
  void hasRole_matchesAndMisses() {
    Exchange ex = exchangeWith(List.of("admin", "editor"), null);
    assertThat(JwtAuthorizationUtils.hasRole(ex, "admin")).isTrue();
    assertThat(JwtAuthorizationUtils.hasRole(ex, "viewer")).isFalse();
    assertThat(JwtAuthorizationUtils.hasRole(exchangeWith(null, null), "admin")).isFalse();
  }

  @Test
  void hasAnyRole_matchesNoneAndNull() {
    Exchange ex = exchangeWith(List.of("editor"), null);
    assertThat(JwtAuthorizationUtils.hasAnyRole(ex, "admin", "editor")).isTrue();
    assertThat(JwtAuthorizationUtils.hasAnyRole(ex, "admin", "viewer")).isFalse();
    assertThat(JwtAuthorizationUtils.hasAnyRole(exchangeWith(null, null), "admin")).isFalse();
  }

  @Test
  void hasAllRoles_allPartialAndNull() {
    Exchange ex = exchangeWith(List.of("admin", "editor"), null);
    assertThat(JwtAuthorizationUtils.hasAllRoles(ex, "admin", "editor")).isTrue();
    assertThat(JwtAuthorizationUtils.hasAllRoles(ex, "admin", "viewer")).isFalse();
    assertThat(JwtAuthorizationUtils.hasAllRoles(exchangeWith(null, null), "admin")).isFalse();
  }

  @Test
  void hasScope_matchesAndMisses() {
    Exchange ex = exchangeWith(null, List.of("read", "write"));
    assertThat(JwtAuthorizationUtils.hasScope(ex, "read")).isTrue();
    assertThat(JwtAuthorizationUtils.hasScope(ex, "delete")).isFalse();
    assertThat(JwtAuthorizationUtils.hasScope(exchangeWith(null, null), "read")).isFalse();
  }

  @Test
  void hasAnyScope_matchesNoneAndNull() {
    Exchange ex = exchangeWith(null, List.of("read"));
    assertThat(JwtAuthorizationUtils.hasAnyScope(ex, "read", "write")).isTrue();
    assertThat(JwtAuthorizationUtils.hasAnyScope(ex, "write", "delete")).isFalse();
    assertThat(JwtAuthorizationUtils.hasAnyScope(exchangeWith(null, null), "read")).isFalse();
  }

  @Test
  void hasAllScopes_allPartialAndNull() {
    Exchange ex = exchangeWith(null, List.of("read", "write"));
    assertThat(JwtAuthorizationUtils.hasAllScopes(ex, "read", "write")).isTrue();
    assertThat(JwtAuthorizationUtils.hasAllScopes(ex, "read", "delete")).isFalse();
    assertThat(JwtAuthorizationUtils.hasAllScopes(exchangeWith(null, null), "read")).isFalse();
  }

  @Test
  void requireRole_passesWhenPresentThrowsWhenMissing() {
    Exchange ex = exchangeWith(List.of("admin"), null);
    assertThatNoException().isThrownBy(() -> JwtAuthorizationUtils.requireRole(ex, "admin"));
    assertThatThrownBy(() -> JwtAuthorizationUtils.requireRole(ex, "viewer"))
        .isInstanceOf(InsufficientPrivilegesException.class)
        .hasMessageContaining("missing role: viewer");
  }

  @Test
  void requireScope_passesWhenPresentThrowsWhenMissing() {
    Exchange ex = exchangeWith(null, List.of("read"));
    assertThatNoException().isThrownBy(() -> JwtAuthorizationUtils.requireScope(ex, "read"));
    assertThatThrownBy(() -> JwtAuthorizationUtils.requireScope(ex, "write"))
        .isInstanceOf(InsufficientPrivilegesException.class)
        .hasMessageContaining("missing scope: write");
  }

  @Test
  void requireRoleAndScope_passesAndThrowsForEachMissingPart() {
    Exchange ok = exchangeWith(List.of("admin"), List.of("read"));
    assertThatNoException()
        .isThrownBy(() -> JwtAuthorizationUtils.requireRoleAndScope(ok, "admin", "read"));

    Exchange missingScope = exchangeWith(List.of("admin"), List.of("write"));
    assertThatThrownBy(
        () -> JwtAuthorizationUtils.requireRoleAndScope(missingScope, "admin", "read"))
        .isInstanceOf(InsufficientPrivilegesException.class);

    Exchange missingRole = exchangeWith(List.of("editor"), List.of("read"));
    assertThatThrownBy(
        () -> JwtAuthorizationUtils.requireRoleAndScope(missingRole, "admin", "read"))
        .isInstanceOf(InsufficientPrivilegesException.class);
  }
}
