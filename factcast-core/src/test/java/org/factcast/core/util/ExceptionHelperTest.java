package org.factcast.core.util;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.NonNull;

@ExtendWith(MockitoExtension.class)
class ExceptionHelperTest {

    @SuppressWarnings("ThrowableNotThrown")
    @Nested
    class WhenToingRuntime {
        @Mock
        private @NonNull Throwable exception;

        @BeforeEach
        void setup() {
        }

        @Test
        void wrapsIfNecessary() {
            assertThat(ExceptionHelper.toRuntime(new IOException("damn"))).isInstanceOf(RuntimeException.class);
        }

        @Test
        void returnsIfWrappingNotNecessary() {
            RuntimeException probe = new RuntimeException("probe");
            assertThat(ExceptionHelper.toRuntime(probe)).isSameAs(probe);
        }
    }
}
