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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.camelbee.constants.CamelBeeConstants;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.debugger.service.RouteContextService;
import org.camelbee.logging.LoggingService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Covers the node id that {@link NodeIdInterceptStrategy} stamps on the exchange, and specifically
 * the cases where Camel's own {@code getHistoryNodeId()} is null: sends performed inside an EIP, and
 * redelivered attempts.
 */
class NodeIdInterceptStrategyTest {

  private final NodeIdInterceptStrategy strategy = new NodeIdInterceptStrategy(tracerService(true));

  /** loggingEnabled alone is enough to make {@link TracerService#isActive()} true or false. */
  private static TracerService tracerService(boolean active) {
    MessageService messageService = new MessageService(1000);
    RouteContextService routeContextService = Mockito.mock(RouteContextService.class);
    return new TracerService(active, false, 60000,
        new ExchangeCreatedEventTracer(),
        new ExchangeSendingEventTracer(messageService, routeContextService),
        new ExchangeSentEventTracer(),
        new ExchangeCompletedEventTracer(),
        messageService,
        new LoggingService());
  }

  @Test
  void stampsTheNodeIdWhileTheNodeRunsAndRestoresItAfterwards() throws Exception {
    NamedNode node = Mockito.mock(NamedNode.class);
    Mockito.when(node.getId()).thenReturn("to1");

    List<String> seenDuringProcessing = new ArrayList<>();
    Processor target = exchange -> seenDuringProcessing.add(exchange.getProperty(CamelBeeConstants.CAMELBEE_NODE_ID, String.class));

    try (CamelContext context = new DefaultCamelContext()) {
      Processor wrapped = strategy.wrapProcessorInInterceptors(context, node, target, null);
      Exchange exchange = new DefaultExchange(context);

      wrapped.process(exchange);

      // the target sees this node...
      assertThat(seenDuringProcessing).containsExactly("to1");
      // ...and the enclosing value is put back, so a later send is not attributed to this node
      assertThat(exchange.getProperty(CamelBeeConstants.CAMELBEE_NODE_ID)).isNull();
    }
  }

  @Test
  void restoresTheEnclosingNodeRatherThanClearing() throws Exception {
    NamedNode node = Mockito.mock(NamedNode.class);
    Mockito.when(node.getId()).thenReturn("inner");

    try (CamelContext context = new DefaultCamelContext()) {
      Processor wrapped = strategy.wrapProcessorInInterceptors(context, node, exchange -> {
      }, null);
      Exchange exchange = new DefaultExchange(context);
      exchange.setProperty(CamelBeeConstants.CAMELBEE_NODE_ID, "outer");

      wrapped.process(exchange);

      assertThat(exchange.getProperty(CamelBeeConstants.CAMELBEE_NODE_ID)).isEqualTo("outer");
    }
  }

  @Test
  void doesNothingWhenNeitherLoggingNorTracingIsActive() throws Exception {
    NamedNode node = Mockito.mock(NamedNode.class);
    Mockito.when(node.getId()).thenReturn("to1");

    NodeIdInterceptStrategy inactiveStrategy = new NodeIdInterceptStrategy(tracerService(false));
    List<String> seenDuringProcessing = new ArrayList<>();
    Processor target = exchange -> seenDuringProcessing.add(exchange.getProperty(CamelBeeConstants.CAMELBEE_NODE_ID, String.class));

    try (CamelContext context = new DefaultCamelContext()) {
      Processor wrapped = inactiveStrategy.wrapProcessorInInterceptors(context, node, target, null);
      Exchange exchange = new DefaultExchange(context);

      wrapped.process(exchange);

      // the target still runs (the strategy must not affect routing), but sees no node id at all -
      // nothing would have read it anyway with both logging and tracing off
      assertThat(seenDuringProcessing).containsExactly((String) null);
    }
  }

  @Test
  void leavesTheTargetUntouchedWhenTheNodeHasNoId() throws Exception {
    NamedNode node = Mockito.mock(NamedNode.class);
    Mockito.when(node.getId()).thenReturn(null);
    Processor target = exchange -> {
    };

    try (CamelContext context = new DefaultCamelContext()) {
      assertThat(strategy.wrapProcessorInInterceptors(context, node, target, null)).isSameAs(target);
    }
  }

  /**
   * The reason the strategy exists. Every send below reports no history node id from Camel: the EIP
   * ones because the sub-exchange copy does not carry it, the retries because the node advices do
   * not run again on redelivery.
   */
  @Test
  void suppliesANodeIdWhereCamelReportsNone() throws Exception {
    Map<String, String> camelNodeId = new java.util.LinkedHashMap<>();
    Map<String, String> camelBeeNodeId = new java.util.LinkedHashMap<>();

    try (CamelContext context = new DefaultCamelContext()) {
      context.setMessageHistory(false);
      context.getCamelContextExtension().addInterceptStrategy(strategy);

      context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {

        @Override
        public void notify(CamelEvent event) {
          if (event instanceof CamelEvent.ExchangeSendingEvent sending) {
            Exchange exchange = sending.getExchange();
            String uri = sending.getEndpoint().getEndpointUri();
            camelNodeId.putIfAbsent(uri,
                ((DefaultExchange) exchange).getExchangeExtension().getHistoryNodeId());
            camelBeeNodeId.putIfAbsent(uri,
                exchange.getProperty(CamelBeeConstants.CAMELBEE_NODE_ID, String.class));
          }
        }
      });

      context.addRoutes(new RouteBuilder() {

        @Override
        public void configure() {
          onException(IllegalStateException.class).maximumRedeliveries(2).redeliveryDelay(1).handled(true);

          from("direct:start").routeId("startRoute")
              .wireTap("direct:tap")
              .enrich("direct:enrich")
              .recipientList().constant("direct:recipient")
              .to("direct:flaky").id("flakyTo");

          from("direct:tap").routeId("tapRoute").to("mock:tap");
          from("direct:enrich").routeId("enrichRoute").to("mock:enrich");
          from("direct:recipient").routeId("recipientRoute").to("mock:recipient");

          from("direct:flaky").routeId("flakyRoute").errorHandler(noErrorHandler())
              .process(exchange -> {
                int attempts = exchange.getProperty("attempts", 0, Integer.class) + 1;
                exchange.setProperty("attempts", attempts);
                if (attempts <= 2) {
                  throw new IllegalStateException("simulated failure " + attempts);
                }
              });
        }
      });

      context.start();
      context.createProducerTemplate().sendBody("direct:start", "hello");
      Thread.sleep(300);
    }

    // Camel supplies nothing for any of these
    assertThat(camelNodeId).containsEntry("direct://tap", null);
    assertThat(camelNodeId).containsEntry("direct://enrich", null);
    assertThat(camelNodeId).containsEntry("direct://recipient", null);

    // the strategy names the EIP node that performed each send. Auto-generated ids carry a
    // JVM-wide counter, so assert the node kind rather than the exact suffix.
    assertThat(camelBeeNodeId.get("direct://tap")).startsWith("wireTap");
    assertThat(camelBeeNodeId.get("direct://enrich")).startsWith("enrich");
    assertThat(camelBeeNodeId.get("direct://recipient")).startsWith("recipientList");

    // and the send that is redelivered keeps naming the node that owns it - an explicit id, so
    // this one is exact
    assertThat(camelBeeNodeId).containsEntry("direct://flaky", "flakyTo");
  }

  /**
   * The response half of a hop is traced after the callee route has run. Without restoring the
   * enclosing node on the way out, the id would still name the callee's last node and the response
   * would be attributed to the callee's edge rather than to the hop's own.
   */
  @Test
  void namesTheCallingNodeAgainOnceTheCalleeRouteHasReturned() throws Exception {
    Map<String, String> atSent = new java.util.LinkedHashMap<>();

    try (CamelContext context = new DefaultCamelContext()) {
      context.setMessageHistory(false);
      context.getCamelContextExtension().addInterceptStrategy(strategy);

      context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {

        @Override
        public void notify(CamelEvent event) {
          if (event instanceof CamelEvent.ExchangeSentEvent sent) {
            atSent.putIfAbsent(sent.getEndpoint().getEndpointUri(),
                sent.getExchange().getProperty(CamelBeeConstants.CAMELBEE_NODE_ID, String.class));
          }
        }
      });

      context.addRoutes(new RouteBuilder() {

        @Override
        public void configure() {
          from("direct:caller").routeId("callerRoute")
              .to("direct:callee").id("callerTo");

          from("direct:callee").routeId("calleeRoute")
              .to("mock:inner").id("calleeTo");
        }
      });

      context.start();
      context.createProducerTemplate().sendBody("direct:caller", "hello");
      Thread.sleep(200);
    }

    // the inner hop is attributed to the callee's node
    assertThat(atSent).containsEntry("mock://inner", "calleeTo");
    // and the outer hop is back to the caller's node, not left holding the callee's
    assertThat(atSent).containsEntry("direct://callee", "callerTo");
  }
}
