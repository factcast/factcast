+++
draft = false
title = "Snapshots"
description = ""


creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"


parent = "factus-projections"
identifier = "factus-snapshotting"
weight = 6

+++

In EventSourcing a Snapshot is used to memorize an object at a certain point in the EventStream, so that when later-on this object has to be retrieved again, 
rather than creating a fresh one and use it to process all relevant events, we can start with the snapshot (that already has the state of the object from before) 
and just process all the facts that happened since.
  
It is easy to see that storing and retrieving snapshots involves some kind of marshalling and unmarshalling, as well as some sort of Key/Value store to keep the snapshots. 

### Snapshot Serialization

Serialization is done using a `SnapshotSerializer`. 

```java

public interface SnapshotSerializer {
    byte[] serialize(SnapshotProjection a);

    <A extends SnapshotProjection> A deserialize(Class<A> type, byte[] bytes);

    boolean includesCompression();
}
```

As you can see, there is no assumption whether it produces JSON or anything, it just has to be symmetric. In order to be able to optimize the transport of the snapshot to/from the SnapshotCache, each `SnapshotSerializer` should indicate if it already includes compression, or if compression in transit might be a good idea.
Factus ships with a default SnapshotSerializer, that - you can guess by now - uses Jackson. Neither the most performant, nor the most compact choice. Feel free to create one on your own.

### Snapshot caching

The Key/Value store that keeps and maintains the snapshots is called a `SnapshotCache`.
 
Factus comes with a default SnapshotCache that uses FactCast to store/retrieve and maintain those cached snapshots. While this works reasonably well and is easy to use, as it does not involve any other piece of infrastructure, you might want to keep an eye on the load- and storage-requirements imposed by this.
It is very easy to provide an implementation of SnapshotCache that uses for instance Redis or memcached instead, so that you keep this load away from FactCast for performance, scalability and in the end also cost efficiency reasons. Also it has an effect on the availability and maybe responsiveness of your application, but this is obviously outside of the scope of this document.

The SnapshotCache by default only keeps the last version of a particular snapshot, and deletes it after 90 days of being unused. 
See [Properties](/setup/properties)

