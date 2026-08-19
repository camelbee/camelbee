package org.camelbee.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoggingAttributeTest {

  @Test
  void getAttributeName_returnsLogName() {
    assertThat(LoggingAttribute.ROUTE_ID.getAttributeName()).isEqualTo("routeId");
  }

  @Test
  void matches_isCaseInsensitiveAndNullSafe() {
    assertThat(LoggingAttribute.ROUTE_ID.matches("ROUTEID")).isTrue();
    assertThat(LoggingAttribute.ROUTE_ID.matches("routeId")).isTrue();
    assertThat(LoggingAttribute.ROUTE_ID.matches("endpoint")).isFalse();
    assertThat(LoggingAttribute.ROUTE_ID.matches(null)).isFalse();
  }

  @Test
  void findByName_returnsMatchOrEmpty() {
    assertThat(LoggingAttribute.findByName("exchangeId")).contains(LoggingAttribute.EXCHANGE_ID);
    assertThat(LoggingAttribute.findByName("EXCEPTION")).contains(LoggingAttribute.EXCEPTION);
    assertThat(LoggingAttribute.findByName("nope")).isEmpty();
    assertThat(LoggingAttribute.findByName(null)).isEmpty();
  }
}
