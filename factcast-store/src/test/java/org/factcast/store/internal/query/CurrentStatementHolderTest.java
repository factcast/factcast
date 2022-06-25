package org.factcast.store.internal.query;

import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentStatementHolderTest {
  @Mock private Statement statement;
  @InjectMocks private CurrentStatementHolder underTest;

  @Nested
  class WhenClosing {
    @BeforeEach
    void setup() {}

    @Test
    void ignoresNull() {
      underTest.close();
    }

    @SneakyThrows
    @Test
    void cancelsStatement() {
      underTest.statement(statement);
      underTest.close();
      verify(statement).cancel();
    }

    @SneakyThrows
    @Test
    void cancelsStatementAndCatchesException() {
      underTest.statement(statement);
      doThrow(SQLException.class).when(statement).cancel();
      underTest.close();
      verify(statement).cancel();
    }
  }
}
