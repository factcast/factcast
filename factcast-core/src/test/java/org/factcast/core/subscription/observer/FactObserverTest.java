package org.factcast.core.subscription.observer;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.UUID;
import lombok.NonNull;
import nl.altindag.console.ConsoleCaptor;
import org.factcast.core.Fact;
import org.factcast.core.subscription.FactStreamInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class FactObserverTest {

  static class TestFactObserver implements FactObserver {
    @Override
    public void onNext(@NonNull Fact element) {}
  }

  @Spy Logger logger = LoggerFactory.getLogger(FactObserver.class);
  @InjectMocks private TestFactObserver underTest;

  @Nested
  class WhenCallingOnMethod {
    @Mock private @NonNull Fact element;

    @Test
    void noEffect() {
      @NonNull UUID ffwd = UUID.randomUUID();
      @NonNull FactStreamInfo info = new FactStreamInfo(1, 2);
      // none of them can throw an exception
      underTest.onNext(element);
      underTest.onFactStreamInfo(info);
      underTest.onFastForward(ffwd);
      underTest.onCatchup();
      underTest.onComplete();

      try (ConsoleCaptor consoleCaptor = new ConsoleCaptor()) {
        IOException e = new IOException();
        underTest.onError(e);
        assertThat(
                consoleCaptor.getErrorOutput().stream()
                    .filter(s -> s.contains("Unhandled onError:")))
            .hasSize(1);
      }
    }
  }
}
