package org.camelbee.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimeProviderExtraTest {

  @Test
  void getCurrentClock_returnsConfiguredClock() {
    Clock clock = Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC);
    TimeProvider provider = new TimeProvider(clock);
    assertThat(provider.getCurrentClock()).isEqualTo(clock);
  }

  @Test
  void setCurrentZone_updatesZone() {
    TimeProvider provider = new TimeProvider();
    provider.setCurrentZone(ZoneId.of("Europe/Berlin"));
    assertThat(provider.getCurrentZone()).isEqualTo(ZoneId.of("Europe/Berlin"));
  }

  @Test
  void setFixedClock_fromClock_pinsTheInstant() {
    TimeProvider provider = new TimeProvider(ZoneOffset.UTC);
    Clock fixed = Clock.fixed(Instant.ofEpochMilli(123456), ZoneOffset.UTC);
    provider.setFixedClock(fixed);
    assertThat(provider.getCurrentClock().instant()).isEqualTo(Instant.ofEpochMilli(123456));
  }
}
