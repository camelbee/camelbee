package org.camelbee.logging.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ThreadLocalIdTest {

  @Test
  void transactionId_setGetRemove() {
    UUID id = UUID.randomUUID();
    TransactionId.set(id);
    assertThat(TransactionId.get()).isEqualTo(id.toString());
    TransactionId.remove();
    assertThatThrownBy(TransactionId::get).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void requestId_setGetRemove() {
    UUID id = UUID.randomUUID();
    RequestId.set(id);
    assertThat(RequestId.get()).isEqualTo(id.toString());
    RequestId.remove();
    assertThatThrownBy(RequestId::get).isInstanceOf(IllegalStateException.class);
  }
}
