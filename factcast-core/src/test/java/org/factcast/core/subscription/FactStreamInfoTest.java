package org.factcast.core.subscription;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactStreamInfoTest {
  private static final long START_SERIAL = 1000;
  private static final long HORIZON_SERIAL = 2000;
  private FactStreamInfo underTest;

  @Nested
  class WhenCalculatingPercentage {
    @Test
    void percentage() {
      assertThat(new FactStreamInfo(START_SERIAL, HORIZON_SERIAL).calculatePercentage(1210))
          .isEqualTo(21);
    }

    @Test
    void handlesZeros() {
      assertThat(new FactStreamInfo(START_SERIAL, HORIZON_SERIAL).calculatePercentage(START_SERIAL))
          .isEqualTo(0);
      assertThat(new FactStreamInfo(0, 1).calculatePercentage(0)).isEqualTo(0);
      assertThat(new FactStreamInfo(100, 100).calculatePercentage(100)).isEqualTo(0);
    }
  }
}
