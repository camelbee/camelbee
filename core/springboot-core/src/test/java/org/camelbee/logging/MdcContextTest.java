package org.camelbee.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MdcContextTest {

  @AfterEach
  void cleanup() {
    MdcContext.clearAll();
  }

  @Test
  void set_andGet_string() {
    MdcContext.set(LoggingAttribute.ROUTE_ID, "route-1");
    assertThat(MdcContext.get(LoggingAttribute.ROUTE_ID)).isEqualTo("route-1");
  }

  @Test
  void set_blankString_clearsAttribute() {
    MdcContext.set(LoggingAttribute.ROUTE_ID, "route-1");
    MdcContext.set(LoggingAttribute.ROUTE_ID, "  ");
    assertThat(MdcContext.get(LoggingAttribute.ROUTE_ID)).isNull();
  }

  @Test
  void set_enumValue() {
    MdcContext.set(LoggingAttribute.MESSAGE_TYPE, LoggingAttribute.ENDPOINT);
    assertThat(MdcContext.get(LoggingAttribute.MESSAGE_TYPE)).isEqualTo("ENDPOINT");
  }

  @Test
  void set_intValue() {
    MdcContext.set(LoggingAttribute.SIZE, 42);
    assertThat(MdcContext.get(LoggingAttribute.SIZE)).isEqualTo("42");
  }

  @Test
  void set_collectionValue_formatsAsBracketedList() {
    MdcContext.set(LoggingAttribute.HEADERS, List.of("a", "b"));
    assertThat(MdcContext.get(LoggingAttribute.HEADERS)).isEqualTo("['a', 'b']");
  }

  @Test
  void set_nullOrEmptyCollection_clearsAttribute() {
    MdcContext.set(LoggingAttribute.HEADERS, "x");
    MdcContext.set(LoggingAttribute.HEADERS, (java.util.Collection<String>) null);
    assertThat(MdcContext.get(LoggingAttribute.HEADERS)).isNull();

    MdcContext.set(LoggingAttribute.HEADERS, "y");
    MdcContext.set(LoggingAttribute.HEADERS, List.of());
    assertThat(MdcContext.get(LoggingAttribute.HEADERS)).isNull();
  }

  @Test
  void getOrDefault_returnsFallbackWhenMissing() {
    assertThat(MdcContext.getOrDefault(LoggingAttribute.ENDPOINT, "default")).isEqualTo("default");
  }

  @Test
  void clear_single_andVarargs() {
    MdcContext.set(LoggingAttribute.ROUTE_ID, "r");
    MdcContext.set(LoggingAttribute.ENDPOINT, "e");
    MdcContext.clear(LoggingAttribute.ROUTE_ID);
    assertThat(MdcContext.get(LoggingAttribute.ROUTE_ID)).isNull();

    MdcContext.set(LoggingAttribute.ROUTE_ID, "r");
    MdcContext.clear(LoggingAttribute.ROUTE_ID, LoggingAttribute.ENDPOINT);
    assertThat(MdcContext.get(LoggingAttribute.ROUTE_ID)).isNull();
    assertThat(MdcContext.get(LoggingAttribute.ENDPOINT)).isNull();
  }

  @Test
  void getContextSnapshot_emptyAndPopulated() {
    assertThat(MdcContext.getContextSnapshot()).isEmpty();
    MdcContext.set(LoggingAttribute.ROUTE_ID, "r");
    assertThat(MdcContext.getContextSnapshot()).containsEntry("routeId", "r");
  }

  @Test
  void set_nullAttribute_throws() {
    assertThatThrownBy(() -> MdcContext.set(null, "v"))
        .isInstanceOf(NullPointerException.class);
  }
}
