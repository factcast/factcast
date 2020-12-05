package org.factcast.factus.projection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Objects;
import java.util.UUID;
import lombok.val;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AggregateTest {

  @Test
  void hashOfId() {
    val a = new TestAggregate();
    UUID id = spy(UUID.randomUUID());
    a.aggregateId(id);

    val result = a.hashCode();
    assertEquals(Objects.hash(id), result);
  }

  @Test
  void equals() {
    val a = new TestAggregate();
    val b = new TestAggregate();
    a.aggregateId(UUID.randomUUID());
    b.aggregateId(UUID.randomUUID());

    assertNotEquals(a, b);
    assertNotEquals(b, a);

    b.aggregateId(a.aggregateId());

    assertEquals(a, b);
    assertEquals(b, a);
  }

  class TestAggregate extends Aggregate {}
}
