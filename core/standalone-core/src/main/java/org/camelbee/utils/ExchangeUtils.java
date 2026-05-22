/*
 * Copyright 2023 Rahmi Ege Karaosmanoglu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camelbee.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ExchangeUtils.
 */
public class ExchangeUtils {

  /**
   * The logger.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeUtils.class);

  private ExchangeUtils() {
    // Private constructor
  }

  /**
   * Return all the headers concatenated.
   *
   * @param exchange The exchange.
   * @return String The headers.
   */
  public static String getHeaders(Exchange exchange) {
    var headerMap = exchange.getIn().getHeaders();
    if (headerMap == null || headerMap.isEmpty()) {
      return StringUtils.EMPTY;
    }

    var headers = new StringBuilder();
    headerMap.forEach((p, q) -> headers.append(p).append(":").append(q).append("\n"));
    return headers.toString();
  }

  /**
   * Reads all kind of bodies and convert to string.
   *
   * @param exchange The Exchange.
   * @return String body.
   */
  @SuppressWarnings("java:S3740")
  public static String readBodyAsString(Exchange exchange, boolean resetBefore) {
    try {
      boolean useMessage = exchange.getMessage() != null && exchange.getMessage().getBody() != null;

      Object bodyObject = useMessage
          ? exchange.getMessage().getBody()
          : exchange.getIn().getBody();

      if (bodyObject == null) {
        return null;
      }

      if (bodyObject instanceof StreamCache streamCache) {
        return processStreamCache(streamCache, resetBefore);
      }
      if (bodyObject instanceof List<?> list) {
        return buildListString(list);
      }
      String converted = itemToString(bodyObject);
      if (converted != null) {
        return converted;
      }
      // Fallback: Camel's type converter handles JAXB, XML Document, etc.
      return useMessage
          ? exchange.getMessage().getBody(String.class)
          : exchange.getIn().getBody(String.class);

    } catch (Exception e) {
      LOGGER.warn("Could not read Exchange body: {}", exchange, e);
      return StringUtils.EMPTY;
    }
  }

  private static String buildListString(List<?> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      Object item = list.get(i);
      if (item instanceof StreamCache sc) {
        try {
          sb.append(processStreamCache(sc, false));
        } catch (IOException e) {
          sb.append(item);
        }
      } else {
        String converted = itemToString(item);
        sb.append(converted != null ? converted : item);
      }
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * Converts known scalar body types to String without side effects.
   * Returns null for unknown types so callers can apply their own fallback.
   */
  private static String itemToString(Object item) {
    return switch (item) {
      case String str -> str;
      case byte[] bytes -> new String(bytes, StandardCharsets.UTF_8);
      case ByteBuffer bb -> readByteBuffer(bb);
      default -> null;
    };
  }

  private static String readByteBuffer(ByteBuffer buffer) {
    ByteBuffer dup = buffer.duplicate();
    byte[] bytes = new byte[dup.remaining()];
    dup.get(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static String processStreamCache(StreamCache streamCache, boolean resetBefore) throws IOException {
    if (resetBefore) {
      streamCache.reset();
    }

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    streamCache.writeTo(byteArrayOutputStream);
    String body = byteArrayOutputStream.toString(StandardCharsets.UTF_8);
    streamCache.reset();

    return body;
  }

}
