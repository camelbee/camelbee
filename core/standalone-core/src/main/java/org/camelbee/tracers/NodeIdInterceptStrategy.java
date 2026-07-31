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

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.camelbee.constants.CamelBeeConstants;

/**
 * Stamps every exchange with the id of the route node currently processing it, so the event tracers
 * can report which node initiated a send.
 *
 * <p>Camel already tracks this as {@code ExchangeExtension.getHistoryNodeId()}, but two properties of
 * that mechanism leave it null exactly where the debugger needs it most:
 *
 * <ul>
 * <li>it lives on the exchange <em>extension</em>, which is not carried into the exchange copies
 * that {@code enrich}, {@code wireTap}, {@code recipientList} and {@code multicast} create - so
 * every EIP sub-send reports no node at all;</li>
 * <li>Camel clears it when the node exits, and a redelivery re-invokes the processor without
 * re-running the node advices - so every retried attempt after the first reports no node.</li>
 * </ul>
 *
 * <p>This strategy avoids both: the id is an exchange <em>property</em>, which copies carry, and it is
 * re-stamped on each pass through the node, which a redelivery does trigger. Without it the UI has to
 * fall back to matching endpoint URIs, which cannot distinguish two outputs of the same route that
 * target the same endpoint - a {@code multicast} and a {@code recipientList} sending to the same
 * route, for example.
 *
 * <p>Note this does not fix every case: a {@code routingSlip} or {@code dynamicRouter} continuation
 * hop is sent after the previous callee route has run, so the property still names that callee's last
 * node. Those remain resolved by URI matching in the UI.
 */
public class NodeIdInterceptStrategy implements InterceptStrategy {

  @Override
  public Processor wrapProcessorInInterceptors(CamelContext camelContext, NamedNode definition,
      Processor target, Processor nextTarget) {

    final String nodeId = definition.getId();
    if (nodeId == null) {
      return target;
    }

    // DelegateAsyncProcessor rather than a plain Processor: wrapping with a synchronous lambda
    // would force every intercepted node onto the caller thread and defeat asynchronous routing.
    return new DelegateAsyncProcessor(target) {

      @Override
      public boolean process(Exchange exchange, AsyncCallback callback) {
        final Object enclosing = exchange.getProperty(CamelBeeConstants.CAMELBEE_NODE_ID);
        exchange.setProperty(CamelBeeConstants.CAMELBEE_NODE_ID, nodeId);

        // Restore the enclosing node on the way out, from the callback rather than a finally block
        // so it also happens when the node completes asynchronously. Without this the id would
        // still name the last node of a callee route once control returned, and the response half
        // of a hop would be attributed to that callee's edge instead of its own.
        return processor.process(exchange, doneSync -> {
          exchange.setProperty(CamelBeeConstants.CAMELBEE_NODE_ID, enclosing);
          callback.done(doneSync);
        });
      }
    };
  }
}
