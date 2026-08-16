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

package org.camelbee.constants;

/**
 * CamelBee Constants.
 */
public final class CamelBeeConstants {

  private CamelBeeConstants() {
    throw new IllegalStateException("Utility class");
  }

  public static final String INITIAL_EXCHANGE_ID = "camelbee-initial-exchange-id";

  public static final String CURRENT_ROUTE_NAME = "camelbee-current-route-name";

  public static final String CURRENT_ROUTE_TRACE_STACK = "camelbee-current-route-stack";

  public static final String SEND_ENDPOINT = "camelbee-send-endpoint";

  public static final String FRAMEWORK = "SpringBoot";

  public static final String SYSTEM_JVM_VENDOR = "java.vendor";

  public static final String SYSTEM_JVM_VERSION = "java.version";

  public static final String CAMEL_FAILED_EVENT_IDENTITY_HASHCODE = "camelbee-failedevent-identity";

  public static final String CAMEL_FAILED_EVENT_ENDPOINT = "camelbee-failedevent-endpoint";

  public static final String CAMELBEE_PRODUCED_EXCHANGE = "camelbee-produced-exchange";

  public static final String MDC_UNITOFWORK_EXECUTED = "camelbee-unitofwork-executed";

  public static final String DIRECT = "direct";

  public static final String LAST_DIRECT_ROUTE = "camelbee-last-direct-route";

  /**
   * Id of the route node currently processing the exchange, stamped by
   * {@code NodeIdInterceptStrategy}. Used as the endpoint id when Camel's own history node id is
   * absent, which it is for every EIP sub-send and every redelivered attempt.
   */
  public static final String CAMELBEE_NODE_ID = "camelbee-node-id";

  public static final String PREVIOUS_EXCHANGE_ID = "camelbee-previous-exchange-id";

  /**
   * Id of the exchange that started this lineage - written once, when an exchange is first traced
   * and the property is absent, and never rewritten afterwards.
   *
   * <p>Never rewriting is the entire point. Exchange properties are carried into copies, so a copy
   * inherits the root's id and reports it as its parent. The aggregating EIPs (multicast, enrich,
   * pollEnrich, recipientList) then copy the branch's properties BACK onto the original when
   * merging results - and because the branch never changed this value, what flows back is the value
   * the original already had. Anything that did change per-exchange would corrupt the original here,
   * which is exactly what an earlier attempt built on {@link #PREVIOUS_EXCHANGE_ID} did: the root
   * ended up parented to its own grandchild, producing cycles.
   *
   * <p>Used only as the fallback. {@code ExchangePropertyKey.CORRELATION_ID} is preferred where
   * Camel sets it, because it names the immediate parent rather than the lineage root; it is absent
   * exactly for wireTap, which removes it from the copy.
   */
  public static final String CAMELBEE_LINEAGE_ROOT = "camelbee-lineage-root";

}
