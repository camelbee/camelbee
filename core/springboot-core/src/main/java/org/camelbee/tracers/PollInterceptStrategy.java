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

package org.camelbee.tracers;

import static org.camelbee.constants.CamelBeeConstants.CURRENT_ROUTE_NAME;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.model.PollDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.camelbee.utils.ExchangeUtils;

/**
 * Traces {@code poll()} and {@code pollEnrich()} hops, which Camel emits no events for.
 *
 * <p>Every other node is returned unwrapped, so this strategy is inert for the rest of a route: only
 * a {@link PollDefinition} or {@link PollEnrichDefinition} is intercepted. See {@link PollEventTracer}
 * for why the hop has to be reconstructed here and exactly what can be observed.
 */
public class PollInterceptStrategy implements InterceptStrategy {

  private final TracerService tracerService;

  public PollInterceptStrategy(TracerService tracerService) {
    this.tracerService = tracerService;
  }

  @Override
  public Processor wrapProcessorInInterceptors(CamelContext camelContext, NamedNode definition,
      Processor target, Processor nextTarget) {

    if (!(definition instanceof PollDefinition) && !(definition instanceof PollEnrichDefinition)) {
      return target;
    }

    final String nodeId = definition.getId();
    if (nodeId == null) {
      return target;
    }

    return new DelegateAsyncProcessor(target) {

      @Override
      public boolean process(Exchange exchange, AsyncCallback callback) {
        // Captured before the poll runs: afterwards the body may have been replaced by whatever was
        // received, and TO_ENDPOINT has been overwritten with the polled endpoint.
        final long start = System.currentTimeMillis();
        final String requestBody = ExchangeUtils.readBodyAsString(exchange, false);
        final String callerRoute = exchange.getProperty(CURRENT_ROUTE_NAME, String.class);

        return processor.process(exchange, doneSync -> {
          try {
            tracerService.tracePollEvent(exchange, nodeId, callerRoute, requestBody,
                System.currentTimeMillis() - start);
          } finally {
            callback.done(doneSync);
          }
        });
      }
    };
  }
}
