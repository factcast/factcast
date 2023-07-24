+++
title = "Publication"
weight = 20
type="docs"
+++

The publishing side is easy and should be intuitive to use. Factus offers a few methods to publish either Events (or Facts if you happen to have handcrafted ones) to FactCast.

```java
public interface Factus extends SimplePublisher, ProjectionAccessor, Closeable {

    /**
     * publishes a single event immediately
     */
    default void publish(@NonNull EventObject eventPojo) {
        publish(eventPojo, f -> null);
    }

    /**
     * publishes a list of events immediately in an atomic manner (all or none)
     */
    default void publish(@NonNull List<EventObject> eventPojos) {
        publish(eventPojos, f -> null);
    }

    /**
     * publishes a single event immediately and transforms the resulting facts
     * to a return type with the given resultFn
     */
    <T> T publish(@NonNull EventObject e, @NonNull Function<Fact, T> resultFn);

    /**
     * publishes a list of events immediately in an atomic manner (all or none)
     * and transforms the resulting facts to a return type with the given
     * resultFn
     */
    <T> T publish(@NonNull List<EventObject> e, @NonNull Function<List<Fact>, T> resultFn);

    /**
     * In case you'd need to assemble a fact yourself
     */
    void publish(@NonNull Fact f);

// ...

```

As you can see, you can either call a void method, or pass a function that translates the published Facts to a return value, in case you need it.

#### Batches

Just like FactCast's `publish(List<Fact>)`, you can publish a list of Events/Facts atomically.

However, in some more complex scenarios, it might be more appropriate to have an object to pass around (and maybe mark aborted) where different parts of the code can contribute Events/Facts to publish to.
This is what **PublishBatch** is used for:

```java
public interface PublishBatch extends AutoCloseable {
    PublishBatch add(EventObject p);

    PublishBatch add(Fact f);

    void execute() throws BatchAbortedException;

    <R> R execute(Function<List<Fact>, R> resultFunction) throws BatchAbortedException;

    PublishBatch markAborted(String msg);

    PublishBatch markAborted(Throwable cause);

    void close(); // checks if either aborted or executed already, otherwise will execute
}

```

In order to use this, just call `Factus::batch` to create a new PublishBatch object.
