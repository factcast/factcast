package org.factcast.core.snap;

import static org.mockito.Mockito.*;

import lombok.NonNull;
import org.factcast.core.store.FactStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactCastSnapshotCacheTest {

  @Mock private @NonNull FactStore store;
  @InjectMocks private FactCastSnapshotCache underTest;

  @Nested
  class WhenGettingSnapshot {
    @Mock private @NonNull SnapshotId id;

    @Test
    void delegates() {
      underTest.getSnapshot(id);
      verify(store).getSnapshot(id);
    }
  }

  @Nested
  class WhenSettingSnapshot {
    @Mock private Snapshot snap;

    @Test
    void delegates() {
      underTest.setSnapshot(snap);
      verify(store).setSnapshot(snap);
    }
  }

  @Nested
  class WhenClearingSnapshot {
    @Mock private @NonNull SnapshotId id;

    @Test
    void delegates() {
      underTest.clearSnapshot(id);
      verify(store).clearSnapshot(id);
    }
  }
}
