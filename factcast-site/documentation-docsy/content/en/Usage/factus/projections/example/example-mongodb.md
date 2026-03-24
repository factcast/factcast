+++
title = "UserNames (MongoDb)"
weight = 1000
type="docs"
+++

Here is a projection that handles _UserCreated_ and _UserDeleted_ events using MongoDB as datastore. Currently, with
MongoDB no transactionally aware implementation is provided.

## Configuration

Before creating the Projection class we need to configure the `com.mongodb.client.MongoClient` and create an instance of `MongoDatabase`.

## Constructing

Create a subscribed projection by extending `AbstractMongoDbSubscribedProjection` and passing a `MongoDatabase` object to the constructor.
The `getScopedName()` helper generates a prefix based on the class name and its current revision, ensuring a fresh collection is created whenever the revision is bumped.
Optionally provide a document class (here `UserSchema`) when requesting the collection so that MongoDB returns typed objects directly.

```java
@Component
@ProjectionMetaData(revision = 1)
public class UserNames extends AbstractMongoDbSubscribedProjection {

  private final MongoCollection<UserSchema> userCollection;

  public UserNames(@NonNull MongoDatabase mongoDatabase) {
    super(mongoDatabase);
    String scopedName = getScopedName().with("userNames").toString();
    userCollection = mongoDatabase.getCollection(scopedName, UserSchema.class);
  }

  // ...

  @Data
  @Builder
  @Accessors(fluent = false)
  public final class UserSchema {
    private UUID id;
    private String firstName;
    private String lastName;
  }
}
```

FactStreamPosition and lock management are automatically handled by `AbstractMongoDbSubscribedProjection`.
For this purpose, two shared collections named `locks` and `states` are created.

## Updating the projection

### Applying Events

Received events are processed inside methods annotated with `@Handler`.

```java
@Handler
void apply(UserCreatedV1 e) {
  userCollection.insertOne(
      UserSchema.builder()
          .id(e.aggregateId())
          .firstName(e.firstName())
          .lastName(e.lastName())
          .build());
}

@Handler
void apply(UserChangedV1 e) {
  userCollection.replaceOne(
      new Document("id", e.aggregateId()),
      UserSchema.builder()
          .id(e.aggregateId())
          .firstName(e.firstName())
          .lastName(e.lastName())
          .build());
}
```

## Full Example

A full example for both subscribed and managed projections can be found [here](https://github.com/factcast/factcast/blob/main/factcast-examples/factcast-example-client-mongodb/src/main/java/org/factcast/example/client/mongodb/ExampleMongoDbClientitests.java).
