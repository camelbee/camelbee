package org.camelbee.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidResolverTest {

  @Test
  void parseUuid_returnsUuidForValidString() {
    UUID uuid = UUID.randomUUID();
    assertThat(UuidResolver.parseUuid(uuid.toString())).contains(uuid);
  }

  @Test
  void parseUuid_emptyForBlankInput() {
    assertThat(UuidResolver.parseUuid(null)).isEmpty();
    assertThat(UuidResolver.parseUuid("")).isEmpty();
    assertThat(UuidResolver.parseUuid("   ")).isEmpty();
  }

  @Test
  void parseUuid_emptyForInvalidString() {
    assertThat(UuidResolver.parseUuid("not-a-uuid")).isEmpty();
  }

  @Test
  void resolveOrGenerate_returnsParsedWhenValid() {
    UUID uuid = UUID.randomUUID();
    assertThat(UuidResolver.resolveOrGenerate(uuid.toString())).isEqualTo(uuid);
  }

  @Test
  void resolveOrGenerate_generatesWhenInvalid() {
    assertThat(UuidResolver.resolveOrGenerate("invalid")).isNotNull();
    assertThat(UuidResolver.resolveOrGenerate(null)).isNotNull();
  }

  @Test
  void generate_returnsNonNullUnique() {
    assertThat(UuidResolver.generate()).isNotEqualTo(UuidResolver.generate());
  }
}
