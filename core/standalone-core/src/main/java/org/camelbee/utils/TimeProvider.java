package org.camelbee.utils;

import static java.util.Objects.requireNonNull;

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

/**
 * A utility class that provides various time-related functionalities.
 * This class allows switching between system clocks and fixed clocks for unit testing,
 * supporting different time zones and clock configurations.
 */
public class TimeProvider {

  /**
   * Default time zone used by the system (UTC).
   */
  protected static final ZoneId DEFAULT_ZONE = ZoneOffset.UTC;

  private static final String INVALID_CLOCK_ERROR = "The 'clock' parameter cannot be null";
  private static final String INVALID_DATETIME_ERROR = "The 'dateTime' parameter cannot be null";
  private static final String INVALID_INSTANT_ERROR = "The 'instant' parameter cannot be null";
  private static final String INVALID_ZONE_ERROR = "The 'zone' parameter cannot be null";

  private Clock currentClock;
  private ZoneId currentZone;

  /**
   * Initializes a new TimeProvider instance with the default time zone (UTC).
   */
  public TimeProvider() {
    this(DEFAULT_ZONE);
  }

  /**
   * Initializes a new TimeProvider instance with the specified time zone.
   *
   * @param zone the time zone to be used, must not be null
   */
  public TimeProvider(final ZoneId zone) {
    this.currentZone = requireNonNull(zone, INVALID_ZONE_ERROR);
    this.currentClock = Clock.system(zone);
  }

  /**
   * Initializes a new TimeProvider instance using the provided Clock.
   * The time zone is derived from the clock.
   *
   * @param clock the clock to be used, must not be null
   */
  public TimeProvider(final Clock clock) {
    this.currentClock = requireNonNull(clock, INVALID_CLOCK_ERROR);
    this.currentZone = clock.getZone();
  }

  // --- Clock Management Methods ---

  /**
   * Retrieves the current clock used by this instance.
   *
   * @return the currently active Clock
   */
  public Clock getCurrentClock() {
    return currentClock;
  }

  /**
   * Updates the clock used by this instance.
   *
   * @param clock the new clock to be set, must not be null
   */
  public void setCurrentClock(final Clock clock) {
    this.currentClock = requireNonNull(clock, INVALID_CLOCK_ERROR);
  }

  /**
   * Retrieves the current time zone used by this instance.
   *
   * @return the currently active ZoneId
   */
  public ZoneId getCurrentZone() {
    return currentZone;
  }

  /**
   * Updates the time zone used by this instance.
   *
   * @param zone the new time zone to be set
   */
  public void setCurrentZone(final ZoneId zone) {
    this.currentZone = zone;
  }

  /**
   * Sets a fixed clock for testing purposes.
   *
   * @param clock the fixed clock to use, must not be null
   */
  public void setFixedClock(final Clock clock) {
    requireNonNull(clock, INVALID_CLOCK_ERROR);
    setFixedClock(clock.instant(), clock.getZone());
  }

  /**
   * Sets a fixed clock using epoch milliseconds, useful for simulating a specific point in time.
   *
   * @param epochMillis the number of milliseconds since the Unix epoch
   */
  public void setFixedClock(final long epochMillis) {
    setFixedClock(Instant.ofEpochMilli(epochMillis), this.currentZone);
  }

  /**
   * Sets a fixed clock using a LocalDateTime, useful for setting a specific local date and time.
   *
   * @param dateTime the local date-time to use, must not be null
   */
  public void setFixedClock(final LocalDateTime dateTime) {
    requireNonNull(dateTime, INVALID_DATETIME_ERROR);
    setFixedClock(dateTime.atZone(this.currentZone).toInstant(), this.currentZone);
  }

  /**
   * Sets a fixed clock using an OffsetDateTime, allowing for precise date-time and zone offset values.
   *
   * @param dateTime the offset date-time to use, must not be null
   */
  public void setFixedClock(final OffsetDateTime dateTime) {
    requireNonNull(dateTime, INVALID_DATETIME_ERROR);
    setFixedClock(dateTime.toInstant(), dateTime.getOffset());
  }

  /**
   * Sets a fixed clock using a ZonedDateTime, which provides full timezone and date-time details.
   *
   * @param dateTime the zoned date-time to use, must not be null
   */
  public void setFixedClock(final ZonedDateTime dateTime) {
    requireNonNull(dateTime, INVALID_DATETIME_ERROR);
    setFixedClock(dateTime.toInstant(), dateTime.getZone());
  }

  /**
   * Sets a fixed clock using a specific Instant.
   *
   * @param instant the instant to set as the fixed time, must not be null
   */
  protected void setFixedClock(final Instant instant) {
    requireNonNull(instant, INVALID_INSTANT_ERROR);
    setCurrentClock(Clock.fixed(instant, this.currentZone));
  }

  /**
   * Sets a fixed clock using a specific Instant and ZoneId.
   *
   * @param instant the instant to set as the fixed time, must not be null
   * @param zone    the time zone to be used, must not be null
   */
  protected void setFixedClock(final Instant instant, final ZoneId zone) {
    requireNonNull(instant, INVALID_INSTANT_ERROR);
    requireNonNull(zone, INVALID_ZONE_ERROR);
    setCurrentClock(Clock.fixed(instant, zone));
  }

  /**
   * Resets the clock to use the system clock instead of a fixed one.
   */
  public void useSystemClock() {
    setCurrentClock(Clock.system(currentZone));
  }

  // --- Time Retrieval Methods ---

  /**
   * Retrieves the current Instant based on the active clock.
   *
   * @return the current Instant
   */
  public Instant currentInstant() {
    return Instant.now(this.currentClock);
  }

  /**
   * Retrieves the current date based on the active clock.
   *
   * @return the current LocalDate
   */
  public LocalDate currentDate() {
    return LocalDate.now(this.currentClock);
  }

  /**
   * Retrieves the current date and time based on the active clock.
   *
   * @return the current LocalDateTime
   */
  public LocalDateTime currentDateTime() {
    return LocalDateTime.now(this.currentClock);
  }

  /**
   * Retrieves the current time based on the active clock.
   *
   * @return the current LocalTime
   */
  public LocalTime currentTime() {
    return LocalTime.now(this.currentClock);
  }

  /**
   * Retrieves the current time in milliseconds based on the active clock.
   *
   * @return the current time in milliseconds
   */
  public long currentMillis() {
    return this.currentClock.millis();
  }

  /**
   * Retrieves the current MonthDay (month and day) based on the active clock.
   *
   * @return the current MonthDay
   */
  public MonthDay currentMonthDay() {
    return MonthDay.now(this.currentClock);
  }

  /**
   * Retrieves the current date and time with offset based on the active clock.
   *
   * @return the current OffsetDateTime
   */
  public OffsetDateTime currentOffsetDateTime() {
    return OffsetDateTime.now(this.currentClock);
  }

  /**
   * Retrieves the current time with offset based on the active clock.
   *
   * @return the current OffsetTime
   */
  public OffsetTime currentOffsetTime() {
    return OffsetTime.now(this.currentClock);
  }

  /**
   * Retrieves the current year based on the active clock.
   *
   * @return the current Year
   */
  public Year currentYear() {
    return Year.now(this.currentClock);
  }

  /**
   * Retrieves the current year and month based on the active clock.
   *
   * @return the current YearMonth
   */
  public YearMonth currentYearMonth() {
    return YearMonth.now(this.currentClock);
  }

  /**
   * Retrieves the current date and time with timezone based on the active clock.
   *
   * @return the current ZonedDateTime
   */
  public ZonedDateTime currentZonedDateTime() {
    return ZonedDateTime.now(this.currentClock);
  }

}