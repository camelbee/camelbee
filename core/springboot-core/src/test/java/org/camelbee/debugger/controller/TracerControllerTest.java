package org.camelbee.debugger.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.camelbee.debugger.model.exchange.MessageListWithInfo;
import org.camelbee.debugger.service.MessageService;
import org.camelbee.tracers.TracerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class TracerControllerTest {

  private TracerController controller;
  private TracerService tracerService;
  private MessageService messageService;

  @BeforeEach
  void setUp() {
    controller = new TracerController();
    tracerService = mock(TracerService.class);
    messageService = mock(MessageService.class);
    controller.tracerService = tracerService;
    controller.messageService = messageService;
  }

  @Test
  void getMessages_keepsTracingActiveAndReturnsList() {
    MessageListWithInfo info = mock(MessageListWithInfo.class);
    when(messageService.getMessagesFrom(2, 5, 1)).thenReturn(info);

    ResponseEntity<MessageListWithInfo> response = controller.getMessages(2, 5, 1);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isSameAs(info);
    verify(tracerService).keepTracingActive();
  }

  @Test
  void deleteMessages_resetsAndReturnsOk() {
    ResponseEntity<String> response = controller.deleteMessages();
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    verify(messageService).reset();
  }

  @Test
  void updateTraceStatus_activateAndDeactivate() throws Exception {
    Class<?> statusType = Class.forName("org.camelbee.debugger.controller.TracerController$TraceStatus");
    Method update = TracerController.class.getMethod("updateTraceStatus", statusType);

    Object active = enumValue(statusType, "ACTIVE");
    Object inactive = enumValue(statusType, "INACTIVE");

    @SuppressWarnings("unchecked")
    ResponseEntity<String> activeResponse = (ResponseEntity<String>) update.invoke(controller, active);
    assertThat(activeResponse.getStatusCode().is2xxSuccessful()).isTrue();
    verify(tracerService).activateTracing(true);

    update.invoke(controller, inactive);
    verify(tracerService).activateTracing(false);
    // keepTracingActive called only on the ACTIVE branch
    verify(tracerService, times(1)).keepTracingActive();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object enumValue(Class<?> type, String name) {
    return Enum.valueOf((Class<Enum>) type.asSubclass(Enum.class), name);
  }
}
