package org.factcast.factus.dynamodb;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.Mockito.*;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import java.util.Collection;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.factus.dynamodb.tx.DynamoDBTransactionalLens;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.factcast.factus.projector.ProjectorPlugin;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DynamoDBProjectorPluginTest {

  @Mock private ProjectorPlugin projectorPlugin;
  @InjectMocks private DynamoDBProjectorPlugin underTest;

  @Nested
  class WhenLensingFor {
    @Mock private Projection p;

    @Test
    void findsTx() {
      assertThat(underTest.lensFor(new TX(mock(AmazonDynamoDB.class))).iterator().next())
          .isInstanceOf(DynamoDBTransactionalLens.class);
    }

    @Test
    void emptyOnNoAnnotation() {
      Collection<ProjectorLens> actual = underTest.lensFor(new None(mock(AmazonDynamoDB.class)));
      Assertions.assertThat(actual).isEmpty();
    }
  }
}

@ProjectionMetaData(serial = 1)
class TX extends ADynamoDBManagedProjection {
  public TX(@NonNull AmazonDynamoDB dynamoDB) {
    super(dynamoDB);
  }
}

@ProjectionMetaData(serial = 1)
class None extends AbstractDynamoDBManagedProjection {
  public None(@NonNull AmazonDynamoDB dynamoDB) {
    super(dynamoDB);
  }
}
