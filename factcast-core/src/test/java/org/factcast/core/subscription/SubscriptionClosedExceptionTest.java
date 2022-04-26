package org.factcast.core.subscription;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionClosedExceptionTest {

  @Test
  void passesException() {
    IOException e = new IOException();
    var uut = new SubscriptionClosedException(e);
    assertThat(uut.getCause()).isSameAs(e);
  }

  @Test
  void passesMessage() {
    var msg = "foo";
    var uut = new SubscriptionClosedException(msg);
    assertThat(uut.getMessage()).isSameAs(msg);
  }
}
