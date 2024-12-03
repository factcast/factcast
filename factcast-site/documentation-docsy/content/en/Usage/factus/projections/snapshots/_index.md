+++
title = "Snapshotting"
weight = 20
type = "docs"
+++

In EventSourcing a Snapshot is used to memorize an object's state at a certain point in the EventStream, so that when later-on this object has to be retrieved again,
rather than creating a fresh one and use it to process all relevant events, we can start with the snapshot (that already has the state of the object from before)
and just process all the facts that happened since.

It is easy to see that storing and retrieving snapshots involves some kind of marshalling and unmarshalling, as well as some sort of Key/Value store to keep the snapshots.

### Snapshot Serialization

Serialization is done using a `SnapshotSerializer`.

```java

public interface SnapshotSerializer {
    byte[] serialize(SnapshotProjection a);

    <A extends SnapshotProjection> A deserialize(Class<A> type, byte[] bytes);

    /**
     * @return displayable name of the serializer. Make sure it is unique, as it is used as part of
     *     the snapshot key
     */
    SnapshotSerializerId id();
}

```

As you can see, there is no assumption whether it produces JSON or anything, it just has to be symmetric.
Factus ships with a default SnapshotSerializer, that - you can guess by now - uses Jackson. Neither the
most performant, nor the most compact choice. Feel free to create one on your own or use provided optional modules
like:
`factcast-factus-serializer-binary` or `factcast-factus-serializer-fury`

### Choosing serializers

If your `SnapshotProjection` does not declare anything different, it will be serialized using the _default SnapshotSerializer_ known to your `SnapshotSerializerSupplier` (when using Spring boot, normally automatically bound as a Spring bean).

In case you want to use a different implementation for a particular 'SnapshotProjection', you can annotate it with '@SerializeUsing'

```java
@SerializeUsing(MySpecialSnapshotSerializer.class)
static class MySnapshotProjection implements SnapshotProjection {
    //...
}
```

Note that those implementations need to have a default constructor and are expected to be stateless.
However, if you use Spring boot those implementations can be Spring beans as well which are then retrieved from the Application Context via the type provided in the annotation.

### Snapshot caching

The Key/Value store that keeps and maintains the snapshots is called a [SnapshotCache]({{< ref "/usage/factus/projections/snapshots/snapshot-caching">}}).

### Revisions

When a projection class is changed (e.g. a field is renamed or its type is changed), depending on the Serializer, there will be a problem with deserialization.
In order to rebuild a snapshot in this case a "revision" is to be provided for the Projection.
Only snapshots that have the same "revision" than the class in its current state will be used.

Revisions are declared to projections by adding a `@ProjectionMetaData(revision = 1L)` to the type.
