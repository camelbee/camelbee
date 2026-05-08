package org.camelbee.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeUtilsTest {

  @Mock
  private Exchange exchange;

  @Mock
  private Message message;

  @Mock
  private StreamCache streamCache;

  @BeforeEach
  void setUp() {
    when(exchange.getIn()).thenReturn(message);
  }

  // --- getHeaders ---

  @Test
  void getHeadersShouldReturnFormattedHeaders() {
    Map<String, Object> headers = new HashMap<>();
    headers.put("header1", "value1");
    headers.put("header2", "value2");
    when(message.getHeaders()).thenReturn(headers);

    String result = ExchangeUtils.getHeaders(exchange);

    assertTrue(result.contains("header1:value1"));
    assertTrue(result.contains("header2:value2"));
    assertTrue(result.contains("\n"));
  }

  @Test
  void getHeadersShouldReturnEmptyStringForEmptyHeaders() {
    when(message.getHeaders()).thenReturn(new HashMap<>());

    assertEquals(StringUtils.EMPTY, ExchangeUtils.getHeaders(exchange));
  }

  @Test
  void getHeadersShouldReturnEmptyStringForNullHeaders() {
    when(message.getHeaders()).thenReturn(null);

    assertEquals(StringUtils.EMPTY, ExchangeUtils.getHeaders(exchange));
  }

  // --- StreamCache ---

  @Test
  void readBodyAsStringShouldHandleStreamCacheWithReset() throws IOException {
    String expected = "Test content";
    when(message.getBody()).thenReturn(streamCache);
    doAnswer(inv -> {
      ((ByteArrayOutputStream) inv.getArgument(0)).write(expected.getBytes());
      return null;
    }).when(streamCache).writeTo(any(ByteArrayOutputStream.class));

    String result = ExchangeUtils.readBodyAsString(exchange, true);

    assertEquals(expected, result);
    verify(streamCache, times(2)).reset(); // once before, once after
  }

  @Test
  void readBodyAsStringShouldHandleStreamCacheWithoutReset() throws IOException {
    String expected = "Test content";
    when(message.getBody()).thenReturn(streamCache);
    doAnswer(inv -> {
      ((ByteArrayOutputStream) inv.getArgument(0)).write(expected.getBytes());
      return null;
    }).when(streamCache).writeTo(any(ByteArrayOutputStream.class));

    String result = ExchangeUtils.readBodyAsString(exchange, false);

    assertEquals(expected, result);
    verify(streamCache, times(1)).reset(); // once after only
  }

  @Test
  void readBodyAsStringShouldHandleStreamCacheIOException() throws IOException {
    when(message.getBody()).thenReturn(streamCache);
    doThrow(new IOException("IO error")).when(streamCache).writeTo(any(ByteArrayOutputStream.class));

    String result = ExchangeUtils.readBodyAsString(exchange, true);

    assertEquals(StringUtils.EMPTY, result);
    verify(streamCache).reset(); // only the resetBefore
  }

  // --- String ---

  @Test
  void readBodyAsStringShouldReturnStringDirectly() {
    String expected = "Test string content";
    when(message.getBody()).thenReturn(expected);

    assertEquals(expected, ExchangeUtils.readBodyAsString(exchange, false));
  }

  // --- byte[] ---

  @Test
  void readBodyAsStringShouldHandleByteArray() {
    byte[] bytes = "hello bytes".getBytes(StandardCharsets.UTF_8);
    when(message.getBody()).thenReturn(bytes);

    assertEquals("hello bytes", ExchangeUtils.readBodyAsString(exchange, false));
  }

  // --- ByteBuffer ---

  @Test
  void readBodyAsStringShouldHandleByteBuffer() {
    byte[] bytes = "buffered content".getBytes(StandardCharsets.UTF_8);
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    when(message.getBody()).thenReturn(buffer);

    String result = ExchangeUtils.readBodyAsString(exchange, false);

    assertEquals("buffered content", result);
    assertEquals(0, buffer.position()); // original buffer must not be drained
  }

  // --- List / Arrays.asList ---

  @Test
  void readBodyAsStringShouldHandleArrayList() {
    ArrayList<String> list = new ArrayList<>(List.of("item1", "item2"));
    when(message.getBody()).thenReturn(list);

    assertEquals("[item1, item2]", ExchangeUtils.readBodyAsString(exchange, false));
  }

  @Test
  void readBodyAsStringShouldHandleArraysAsListNotInstanceOfArrayList() {
    // Arrays.asList returns Arrays$ArrayList which is NOT instanceof java.util.ArrayList —
    // the original bug caused this to fall through to getBody(String.class) and drain ByteBuffers
    List<Object> row = Arrays.asList("createOrder", "READY", "json");
    when(message.getBody()).thenReturn(row);

    assertEquals("[createOrder, READY, json]", ExchangeUtils.readBodyAsString(exchange, false));
  }

  @Test
  void readBodyAsStringShouldHandleListWithByteBufferWithoutDrainingIt() {
    // Reproduces the Cassandra CQL component bug: flat column-value list with a ByteBuffer payload
    byte[] payload = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
    ByteBuffer buffer = ByteBuffer.wrap(payload);
    List<Object> row = Arrays.asList("98c4f97a", "createOrder", "READY", "json", buffer, "txn-id");
    when(message.getBody()).thenReturn(row);

    String result = ExchangeUtils.readBodyAsString(exchange, false);

    assertTrue(result.contains("{\"id\":1}"));
    assertEquals(0, buffer.position()); // buffer must not be drained
  }

  @Test
  void readBodyAsStringShouldHandleListWithByteArray() {
    byte[] payload = "raw bytes".getBytes(StandardCharsets.UTF_8);
    List<Object> list = Arrays.asList("key", payload);
    when(message.getBody()).thenReturn(list);

    String result = ExchangeUtils.readBodyAsString(exchange, false);

    assertTrue(result.contains("raw bytes"));
  }

  @Test
  void readBodyAsStringShouldHandleListWithStreamCache() throws IOException {
    String cachedContent = "cached body";
    StreamCache itemCache = mock(StreamCache.class);
    doAnswer(inv -> {
      ((ByteArrayOutputStream) inv.getArgument(0)).write(cachedContent.getBytes());
      return null;
    }).when(itemCache).writeTo(any(ByteArrayOutputStream.class));

    List<Object> list = Arrays.asList("prefix", itemCache);
    when(message.getBody()).thenReturn(list);

    String result = ExchangeUtils.readBodyAsString(exchange, false);

    assertTrue(result.contains(cachedContent));
    verify(itemCache).reset(); // StreamCache element must be reset after reading
  }

  // --- null / exception ---

  @Test
  void readBodyAsStringShouldReturnNullForNullBody() {
    when(message.getBody()).thenReturn(null);

    assertNull(ExchangeUtils.readBodyAsString(exchange, false));
  }

  @Test
  void readBodyAsStringShouldReturnEmptyStringOnException() {
    when(message.getBody()).thenThrow(new RuntimeException("boom"));

    assertEquals(StringUtils.EMPTY, ExchangeUtils.readBodyAsString(exchange, false));
  }
}
