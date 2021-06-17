package org.factcast.core.subscription.observer;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FastForwardTargetTest {

  @InjectMocks private FastForwardTarget underTest;

  @Nested
  class WhenForingTest {
    @Test
    void createsAnyInstance() {
      assertThat(FastForwardTarget.forTest()).isNotNull();
    }
  }

  @Nested
  class WhenOfing {
    private final UUID ID = UUID.randomUUID();
    private final long SER = 67;

    @Test
    void passesValues() {
      assertThat(FastForwardTarget.of(ID, SER))
          .extracting(FastForwardTarget::targetId)
          .isEqualTo(ID);
      assertThat(FastForwardTarget.of(ID, SER))
          .extracting(FastForwardTarget::targetSer)
          .isEqualTo(SER);
    }
  }
}
