package org.factcast.store.pgsql.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledRegistryRefresherTest {

    @Mock
    private SchemaRegistry registry;
    @InjectMocks
    private ScheduledRegistryRefresher underTest;

    @Nested
    class WhenRefreshing {
        @BeforeEach
        void setup() {
        }

        @Test
        void testRefreshIsPassedThrough() {
            underTest.refresh();
            verify(registry).refresh();
        }
    }
}
