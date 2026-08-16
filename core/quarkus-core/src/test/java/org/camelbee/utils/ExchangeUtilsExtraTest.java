package org.camelbee.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Covers the body-conversion branches of {@link ExchangeUtils#readBodyAsString} (List, byte[] and
 * ByteBuffer payloads) using a real exchange.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExchangeUtilsExtraTest {

  private DefaultCamelContext camelContext;

  @BeforeAll
  void start() {
    camelContext = new DefaultCamelContext();
    camelContext.start();
  }

  @AfterAll
  void stop() {
    camelContext.stop();
  }

  private Exchange exchangeWithBody(Object body) {
    Exchange exchange = new DefaultExchange(camelContext);
    exchange.getMessage().setBody(body);
    return exchange;
  }

  @Test
  void readsByteArrayBodyAsUtf8String() {
    Exchange exchange = exchangeWithBody("hello".getBytes(StandardCharsets.UTF_8));
    assertThat(ExchangeUtils.readBodyAsString(exchange, false)).isEqualTo("hello");
  }

  @Test
  void readsByteBufferBody() {
    Exchange exchange = exchangeWithBody(ByteBuffer.wrap("buf".getBytes(StandardCharsets.UTF_8)));
    assertThat(ExchangeUtils.readBodyAsString(exchange, false)).isEqualTo("buf");
  }

  @Test
  void readsListBodyAsBracketedString() {
    Exchange exchange = exchangeWithBody(List.of("a", "b"));
    assertThat(ExchangeUtils.readBodyAsString(exchange, false)).isEqualTo("[a, b]");
  }

  @Test
  void readsListBodyWithByteArrayItem() {
    Exchange exchange = exchangeWithBody(
        List.of("a".getBytes(StandardCharsets.UTF_8), "b"));
    assertThat(ExchangeUtils.readBodyAsString(exchange, false)).isEqualTo("[a, b]");
  }

  @Test
  void nullBodyReturnsNull() {
    Exchange exchange = new DefaultExchange(camelContext);
    assertThat(ExchangeUtils.readBodyAsString(exchange, false)).isNull();
  }

  @Test
  void readsStreamCacheBody() {
    Exchange exchange = exchangeWithBody(new InputStreamCache("stream".getBytes(StandardCharsets.UTF_8)));
    assertThat(ExchangeUtils.readBodyAsString(exchange, false)).isEqualTo("stream");
  }

  @Test
  void readsListBodyWithStreamCacheAndNonConvertibleItems() {
    Exchange exchange = exchangeWithBody(List.of(
        new InputStreamCache("sc".getBytes(StandardCharsets.UTF_8)),
        Integer.valueOf(42)));
    // StreamCache item is read; the non-convertible Integer falls back to its toString().
    assertThat(ExchangeUtils.readBodyAsString(exchange, false)).isEqualTo("[sc, 42]");
  }
}
