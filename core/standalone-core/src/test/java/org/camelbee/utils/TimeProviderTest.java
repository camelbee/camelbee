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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimeProviderTest {

  private TimeProvider timeProvider;
  private static final ZoneId TEST_ZONE = ZoneId.of("Europe/London");
  private static final Instant FIXED_INSTANT = Instant.parse("2024-01-06T10:15:30.00Z");

  @BeforeEach
  void setUp() {
    timeProvider = new TimeProvider();
  }

  @Test
  void defaultConstructorShouldUseUtcZone() {
    assertEquals(ZoneOffset.UTC, timeProvider.getCurrentZone());
  }

  @Test
  void zoneConstructorShouldUseProvidedZone() {
    TimeProvider zoneProvider = new TimeProvider(TEST_ZONE);
    assertEquals(TEST_ZONE, zoneProvider.getCurrentZone());
  }

  @Test
  void clockConstructorShouldUseClockZone() {
    Clock fixedClock = Clock.fixed(FIXED_INSTANT, TEST_ZONE);
    TimeProvider clockProvider = new TimeProvider(fixedClock);
    assertEquals(TEST_ZONE, clockProvider.getCurrentZone());
  }

  @Test
  void constructorShouldThrowExceptionForNullZone() {
    assertThrows(NullPointerException.class, () -> new TimeProvider((ZoneId) null));
  }

  @Test
  void constructorShouldThrowExceptionForNullClock() {
    assertThrows(NullPointerException.class, () -> new TimeProvider((Clock) null));
  }

  @Test
  void setFixedClockWithInstantShouldSetCorrectTime() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    assertEquals(FIXED_INSTANT, timeProvider.currentInstant());
  }

  @Test
  void setFixedClockWithEpochMillisShouldSetCorrectTime() {
    long epochMillis = FIXED_INSTANT.toEpochMilli();
    timeProvider.setFixedClock(epochMillis);
    assertEquals(epochMillis, timeProvider.currentMillis());
  }

  @Test
  void setFixedClockWithLocalDateTimeShouldSetCorrectTime() {
    LocalDateTime dateTime = LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);
    timeProvider.setFixedClock(dateTime);
    assertEquals(dateTime, timeProvider.currentDateTime());
  }

  @Test
  void setFixedClockWithOffsetDateTimeShouldSetCorrectTime() {
    OffsetDateTime dateTime = OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);
    timeProvider.setFixedClock(dateTime);
    assertEquals(dateTime.toInstant(), timeProvider.currentInstant());
  }

  @Test
  void setFixedClockWithZonedDateTimeShouldSetCorrectTime() {
    ZonedDateTime dateTime = ZonedDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);
    timeProvider.setFixedClock(dateTime);
    assertEquals(dateTime.toInstant(), timeProvider.currentInstant());
  }

  @Test
  void useSystemClockShouldResetToSystemTime() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    timeProvider.useSystemClock();
    assertNotEquals(FIXED_INSTANT, timeProvider.currentInstant());
  }

  @Test
  void currentInstantShouldReturnCorrectInstant() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    assertEquals(FIXED_INSTANT, timeProvider.currentInstant());
  }

  @Test
  void currentDateShouldReturnCorrectDate() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    assertEquals(
        LocalDate.ofInstant(FIXED_INSTANT, timeProvider.getCurrentZone()),
        timeProvider.currentDate()
    );
  }

  @Test
  void currentDateTimeShouldReturnCorrectDateTime() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    assertEquals(
        LocalDateTime.ofInstant(FIXED_INSTANT, timeProvider.getCurrentZone()),
        timeProvider.currentDateTime()
    );
  }

  @Test
  void currentTimeShouldReturnCorrectTime() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    assertEquals(
        LocalTime.ofInstant(FIXED_INSTANT, timeProvider.getCurrentZone()),
        timeProvider.currentTime()
    );
  }

  @Test
  void currentMillisShouldReturnCorrectMilliseconds() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    assertEquals(FIXED_INSTANT.toEpochMilli(), timeProvider.currentMillis());
  }

  @Test
  void currentMonthDayShouldReturnCorrectMonthDay() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    assertEquals(
        MonthDay.from(LocalDate.ofInstant(FIXED_INSTANT, timeProvider.getCurrentZone())),
        timeProvider.currentMonthDay()
    );
  }

  @Test
  void currentOffsetDateTimeShouldReturnCorrectOffsetDateTime() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    assertEquals(
        OffsetDateTime.ofInstant(FIXED_INSTANT, timeProvider.getCurrentZone()),
        timeProvider.currentOffsetDateTime()
    );
  }

  @Test
  void currentOffsetTimeShouldReturnCorrectOffsetTime() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    assertEquals(
        OffsetTime.ofInstant(FIXED_INSTANT, timeProvider.getCurrentZone()),
        timeProvider.currentOffsetTime()
    );
  }

  @Test
  void currentYearShouldReturnCorrectYear() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    assertEquals(
        Year.from(LocalDate.ofInstant(FIXED_INSTANT, timeProvider.getCurrentZone())),
        timeProvider.currentYear()
    );
  }

  @Test
  void currentYearMonthShouldReturnCorrectYearMonth() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    assertEquals(
        YearMonth.from(LocalDate.ofInstant(FIXED_INSTANT, timeProvider.getCurrentZone())),
        timeProvider.currentYearMonth()
    );
  }

  @Test
  void currentZonedDateTimeShouldReturnCorrectZonedDateTime() {
    timeProvider.setFixedClock(FIXED_INSTANT);
    assertEquals(
        ZonedDateTime.ofInstant(FIXED_INSTANT, timeProvider.getCurrentZone()),
        timeProvider.currentZonedDateTime()
    );
  }
}
