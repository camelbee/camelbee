package org.camelbee.notifier;

import org.apache.camel.impl.event.AbstractExchangeEvent;
import org.apache.camel.impl.event.ExchangeCreatedEvent;
import org.apache.camel.impl.event.ExchangeSendingEvent;
import org.apache.camel.impl.event.ExchangeSentEvent;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.camelbee.tracers.TracerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CamelBeeEventNotifier for subscribing Camel Exchange Events.
 * If you make this class a spring bean then events are notified twice!
 * Be aware.
 *
 */
public class CamelBeeEventNotifier extends EventNotifierSupport {

  /**
   * The logger.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(CamelBeeEventNotifier.class);

  final TracerService tracerService;

  public CamelBeeEventNotifier(TracerService tracerService) {
    this.tracerService = tracerService;
  }

  @Override
  public void notify(CamelEvent event) throws Exception {

    if (event instanceof ExchangeCreatedEvent exchangeCreatedEvent) {
      tracerService.traceExchangeCreateEvent(exchangeCreatedEvent);
    } else if (event instanceof ExchangeSendingEvent exchangeSendingEvent) {
      tracerService.traceExchangeSendingEvent(exchangeSendingEvent);
    } else if (event instanceof ExchangeSentEvent exchangeSentEvent) {
      tracerService.traceExchangeSentEvent(exchangeSentEvent);
    } else if (event instanceof ExchangeCompletedEvent exchangeCompletedEvent) {
      tracerService.traceExchangeCompletedEvent(exchangeCompletedEvent);
    } else {
      LOGGER.trace("Event type not traced: {}", event.getClass().getName());
    }

  }

  /**
   * Checks if the given Camel event is enabled for notification.
   *
   * @param event The CamelEvent to check.
   * @return true if the event is enabled for notification, otherwise false.
   */
  @Override
  public boolean isEnabled(CamelEvent event) {
    return event instanceof AbstractExchangeEvent;
  }

}
