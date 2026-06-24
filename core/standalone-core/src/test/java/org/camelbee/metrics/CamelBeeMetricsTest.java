package org.camelbee.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.camel.component.micrometer.MicrometerConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

class CamelBeeMetricsTest {

  @Test
  void install_bindsRegistryAndStartsWithoutManagementServer() throws Exception {
    DefaultCamelContext camelContext = new DefaultCamelContext();

    CamelBeeMetrics.install(camelContext);

    // A Prometheus-backed MeterRegistry is bound under the conventional camel-micrometer name.
    MeterRegistry registry = camelContext.getRegistry()
        .lookupByNameAndType(MicrometerConstants.METRICS_REGISTRY_NAME, MeterRegistry.class);
    assertThat(registry).isNotNull();

    // Starting the context triggers the startup listener; with no management server present it
    // logs a warning and returns without exposing the scrape endpoint (no exception).
    assertThatNoException().isThrownBy(camelContext::start);

    camelContext.stop();
  }
}
