package org.factcast.store.internal.listen;

import static org.assertj.core.api.Assertions.*;

import org.factcast.store.internal.listen.PgListener.Signal;
import org.junit.jupiter.api.*;

class SignalDeduplicationSetTest {

  @Test
  void dedups() {
    SignalDeduplicationSet uut = new SignalDeduplicationSet(2);
    assertThat(uut.add(new Signal("","","","1"))).isTrue();
    assertThat(uut.add(new Signal("","","","1"))).isFalse();
    assertThat(uut.add(new Signal("","","","1"))).isFalse();
  }

  @Test
  void trims() {
    SignalDeduplicationSet uut = new SignalDeduplicationSet(2);
    assertThat(uut.add(new Signal("","","","1"))).isTrue();
    assertThat(uut.add(new Signal("","","","2"))).isTrue();
    assertThat(uut.add(new Signal("","","","3"))).isTrue();

    // two and three should be in
    assertThat(uut.add(new Signal("","","","2"))).isFalse();
    assertThat(uut.add(new Signal("","","","3"))).isFalse();
    // 1 should have been trimmed
    assertThat(uut.add(new Signal("","","","1"))).isTrue();

  }

}
