package org.factcast.store.pgsql.internal.tail;

import org.factcast.store.pgsql.PgConfigurationProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PGTailIndexManagerImplTest {
  // TODO uwe
  @Mock private JdbcTemplate jdbc;
  @Mock private PgConfigurationProperties props;
  @Mock private PGTailIndexManagerImpl.HighWaterMark target;
  @InjectMocks private PGTailIndexManagerImpl underTest;

  @Nested
  class WhenTriggeringTailCreation {
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenRemovingIndex {
    private final String INDEX_NAME = "INDEX_NAME";

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenTimingToCreateANewTail {
    private final String STRING = "STRING";

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenCreatingNewTail {
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenTargetingId {
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenTargetingSer {
    @BeforeEach
    void setup() {}
  }
}
