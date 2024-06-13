package org.factcast.server.ui.plugins;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.ParseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JsonPayloadTest {

    private static final String JSON = "JSON";
    @Mock
    private ParseContext parseContext;
    @Mock
    private ParseContext pathReturningContext;
    @Mock
    private DocumentContext documentContext;
    @Mock
    private DocumentContext pathReturningDocumentContext;
    @InjectMocks
    private JsonPayload underTest;

    @Nested
    class WhenFindingPaths {
        private final String PATH = "PATH";

        @BeforeEach
        void setup() {
        }
    }

    @Nested
    class WhenFindingAnyPaths {
        @BeforeEach
        void setup() {
        }
    }

    @Nested
    class WhenGetting {
        private final String PATH = "PATH";

        @BeforeEach
        void setup() {
        }
    }

    @Nested
    class WhenReading {
        private final String PATH = "PATH";
        @Mock
        private Class<T> clazz;

        @BeforeEach
        void setup() {
        }
    }

    @Nested
    class WhenAdding {
        private final String PATH = "PATH";
        @Mock
        private Object value;

        @BeforeEach
        void setup() {
        }
    }

    @Nested
    class WhenRemoving {
        private final String PATH = "PATH";

        @BeforeEach
        void setup() {
        }
    }

    @Nested
    class WhenGettingPayload {
        @BeforeEach
        void setup() {
        }
    }
}
