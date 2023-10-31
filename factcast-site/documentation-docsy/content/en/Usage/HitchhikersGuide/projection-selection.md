---
title: "Hitchhiker's Guide To Projection Selection"
weight: 10
type: docs
---

{{% alert title="Preface" %}}

This guide is targeting an audience that is already informed about the projection types offered by Factus, and about
their concepts.

We suggest to check out the "Projections" section of the [Factus API docs]({{< ref "/usage/factus/projections">}})
before delving into this guide.

{{% / alert %}}

## Introduction

Projections, the derived views of our event-sourced data, serve as vital components in shaping our applications,
enabling efficient querying, analysis, and decision-making. However, with Factus offering a range of projection options,
each with its own strengths and considerations, it becomes essential to choose wisely.

Our objective is to equip you with the knowledge and insights necessary to navigate the available options and make the
right choices that align with your project's requirements.
We will delve into the intricacies of each projection type, uncover their unique features and trade-offs, and provide
practical advice to aid your decision-making process.

---

## Identifying Relevant Requirements

Before diving into the exploration of different projection types, it is essential to establish a clear understanding of
the requirements that are relevant to your specific project. By identifying these requirements upfront, you can
effectively narrow down the options and choose the projection type that best aligns with your project goals and
constraints.

In this section, we will delve into a comprehensive list of possible requirements that should be considered when
evaluating projection types. By examining and prioritizing these requirements, you will gain valuable insights into the
trade-offs and considerations associated with each projection type.

### Scalability

This requirement focuses on the ability of the chosen projection type to handle growing amounts of data and increasing
workloads without compromising performance or functionality. Considerations include the horizontal scalability of the
projection, the efficiency of data distribution, and the ability to handle concurrent updates and queries.

### Performance (split between latencies and costs?)

Performance refers to the speed and responsiveness of the projection type in processing events and serving queries.
It involves evaluating factors such as event ingestion rates, query response times, and the impact of increasing data
volumes on overall system performance. Choosing a projection type that can meet the desired performance benchmarks is
crucial for maintaining a high-performing and responsive system.

### Query flexibility

Query flexibility assesses the ability to express complex queries and retrieve relevant information efficiently. It
involves evaluating the projection type's support for various query patterns, such as filtering, aggregations, joins,
and ad-hoc queries. Consider whether the chosen projection type enables the desired flexibility in querying the
event-sourced data while maintaining good performance.

### Complexity

Complexity refers to the level of intricacy and sophistication involved in implementing and managing the chosen
projection type. It encompasses aspects such as the learning curve for developers, the architectural complexity of the
projection, and the degree of operational complexity. It is important to assess whether the complexity aligns with the
team's expertise and resources.

### Data consistency

Data consistency focuses on ensuring that the derived views produced by the projection type accurately reflect the state
of the event stream. It involves assessing how well the projection type handles events, updates, and concurrent
modifications to maintain a consistent and coherent view of the data across different projections. Ensuring data
consistency is crucial for making reliable and accurate decisions based on the derived views.

### Maintainability

Maintainability assesses the ease of managing, updating, and evolving the projection type over time. Considerations
include the ability to accommodate changing business requirements, the ease of making modifications or adding new
features, and the availability of monitoring and debugging tools. Choosing a projection type that is maintainable
ensures long-term sustainability and adaptability of the system.

---

## Comparing Projection Types

Let's go over a comparative analysis of the different projection types, discussing the strengths and weaknesses of each
type concerning the identified requirements.

### Snapshot Projection

[Snapshot Projection documentation]({{< ref "/usage/factus/projections/types/snapshot-projections">}})

- **Scalability**: by default, a Snapshot Projection stores its cached state in the FactCast server (aka the Event
  Store). This can create bottlenecks, or impact the performance of the Event Store, whenever the workload increases.
  It can be optimized, depending on the use case, by hooking into the snapshot lifecycle and changing the way it is
  accessed, serialized, stored, and retained. Alternatively, the _factcast-snapshotcache-redisson_ module can be used,
  to easily store the snapshots in a Redis cluster (see Best Practices and Tips section below).

- **Performance**: whenever the projection is fetched for new events, the most recent snapshot is transferred,
  de-serialized, updated, then re-serialized, and transferred back to the Event Store. Performance might decrease with
  the increase of the snapshots size, and/or the frequency of the queries.

- **Query flexibility**: a Snapshot Projection allows to query, and aggregate, multiple event types on-demand. Any type
  of data structure can be used to store the projected events, considering that it needs to be serializable.

- **Complexity**: the complexity of this projection varies, as it's fairly easy to use, with the default snapshot
  lifecycle implementation. It can get more complex, whenever it's necessary to customize its aspects.

- **Data consistency**: when fetched, the Snapshot Projection ensures to return the most recent representation of the
  event stream. It supports optimistic locking to handle concurrent modifications. Check out the
  [Optimistic Locking]({{< ref "/usage/factus/optimistic-locking">}}) section for further details.

- **Maintainability**: the projection allows to change the business logic of the event handlers, to create new data
  structures for the derived views, update existing ones, or to add new decisions. To do so, it is necessary to update
  the serial of the projection every time the projection class is changed - check out the
  [documentation]({{< ref "/usage/factus/projections/snapshotting#serials">}}).
  The full event-stream will then be re-consumed on the subsequent fetch, as the previously created snapshots will get
  invalidated. The snapshots retention might need to be fine-tuned on the long run, based on the amount of resources
  available, and the frequency of the queries.

### Aggregate

[Aggregate documentation]({{< ref "/usage/factus/projections/types/aggregates">}})

- **Scalability**: same considerations made for the Snapshot Projection apply.

- **Performance**: same considerations made for the Snapshot Projection apply.

- **Query flexibility**: depending on the events schema design, an Aggregate Projection offers limited flexibility,
  compared to a snapshot projection, as it allows to build views that are specific to a single entity (or aggregate,
  hence the name of this projection). This doesn't restrict to perform multiple queries for different aggregate ids, to
  create relations between them. It could be argued that alternative projection types may be better suited for these
  types of use cases, thereby reducing the number of requests sent to the server.

- **Complexity**: same considerations made for the Snapshot Projection, but conceptually speaking, this is the easiest
  projection to use, as it should just represent the state of a single entity.

- **Data consistency**: same considerations made for the Snapshot Projection apply.

- **Maintainability**: same considerations made for the Snapshot Projection apply.

### Managed Projection

[Managed Projection documentation]({{< ref "/usage/factus/projections/types/managed-projection">}})

Preface: considering a Managed Projection that has its state externalized in a shared database.

- **Scalability**: a Managed Projection enables the application to effectively handle the lifecycle of the views,
  allowing to adapt it to the expected workload. With a shared database, the derived views are uniformly accessible and
  consistent among multiple instances of the projection.

- **Performance**: whenever a projection is updated, the most recent events since the last update are fetched from the
  Event Store, and processed. The performance of the projection depends on the frequency of the updates, the amount of
  events that need to be processed, and of course, the complexity of the business logic to manage the derived views.

- **Query flexibility**: a Managed Projection allows to query, and aggregate, multiple event types on-demand. Any type
  of external datasource can be potentially used to store the derived views. Since Factus has no control over the
  Projection, the projection implementation itself needs to ensure that proper concurrency handling is implemented,
  whenever the underlying datasource doesn't support it.

- **Complexity**: this projection requires to implement the business logic to manage the derived views, and to handle
  the concurrency (if needed). It might also be necessary to design the lifecycle of the projection, to ensure that it
  is updated at the desired frequency (e.g. using scheduled updates).

- **Data consistency**: since a shared datasource is used to store the derived views, the same state is shared across
  all instances of the projection. Of course, the derived views might be stale, whenever new events are published, but
  the projection can be updated while processing queries, to ensure that the most recent state is returned. It supports
  optimistic locking to handle concurrent modifications. Check out the
  [Optimistic Locking]({{< ref "/usage/factus/optimistic-locking">}}) section for further details.

- **Maintainability**: a managed projection enables the construction of derived views that can potentially be queried
  even when the Event Store is unavailable. The projection allows to change the business logic of the event handlers,
  and change the underlying structure the derived views. To do so, it is necessary to update the serial of the
  projection every time the projection class is changed - check out the
  [documentation]({{< ref "/usage/factus/projections/snapshotting#serials">}}). The full event-stream will then be
  re-consumed on the subsequent updates, to rebuild the derived views.

### Local Managed Projection

[Local Managed Projection documentation]({{< ref "/usage/factus/projections/types/local-managed-projection">}})

- **Scalability**: a Local Managed Projection stores its state in-memory. Depending on the use-case, this can create
  performance, and availability issues on the long-run, whenever the derived views size increases over time or is
  affected by peaks. Remember that, during horizontal scaling, each instance will maintain its independent state,
  potentially resulting in data inconsistencies.

- **Performance**: same considerations made for the Managed Projection. Arguably, the performance of a Local Managed
  Projection is better, as it doesn't need to access an external datasource to store the derived views. However, it
  needs to be considered that the derived views are stored in-memory, and that the memory footprint of the projection
  will increase over time, potentially affecting the performance of the application.

- **Query flexibility**: a Local Managed Projection offers the highest degree of freedom, in terms of flexibility, as it
  enable to manage the in-memory views using whatever data structure offered by the programming language.

- **Complexity**: this projection only requires to implement the business logic to manage the derived views. For this
  reason, it is probably the easiest projection to start with, especially for a proof of concept, or a prototype.

- **Data consistency**: since the derived views are stored in-memory, the same state won't be shared across multiple
  instances. In terms of staleness, the same considerations made for the Managed Projection apply.

- **Maintainability**: a Local Managed Projection is the easiest projection to maintain, as it doesn't require to
  manage external datasources. Everytime the application is stopped, the derived views are lost, and need to be rebuilt
  on subsequent restarts: this allows to easily test the projection, and change its business logic, but also has an
  impact on the performances, as the derived views need to be rebuilt from scratch.

### Subscribed Projection

[Subscribed Projection documentation]({{< ref "/usage/factus/projections/types/subscribed-projection">}})

Preface: considering a Subscribed Projection that has its state externalized in a shared database.

- **Scalability**: only one instance will actually subscribe to the event stream, and receive events asynchronously.
  This implies that horizontal scaling could be limited, as only one instance will be able to execute the handlers
  business logic. However, with a shared database, the derived views are uniformly accessible and consistent among
  multiple instances of the projection, enabling to spread the load of the queries.

- **Performance**: after catching-up, the projection consumes events right after those are published, with a small
  latency (expected to be below 100ms). The projection performance only depends on the complexity of the business logic,
  and the underlying datasource used to store the derived views.

- **Query flexibility**: a Managed Projection allows to query, and aggregate, multiple event types on-demand. Any type
  of external datasource can be potentially used to store the derived views. Since Factus has no control over the
  Projection, the projection implementation itself needs to ensure that proper concurrency handling is implemented,
  whenever the underlying datasource doesn't support it. Since the derived views are updated asynchronously, it is
  possible to query only the most recent state of the derived views, and not the state at the time of the query.

- **Complexity**: this projection requires to implement the business logic to manage the derived views, and to handle
  the concurrency (if needed). The projection update is handled asynchronously by Factus, reducing the complexity of
  the application.

- **Data consistency**: since a shared datasource is used to store the derived views, the same state is shared across
  all instances of the projection. Since the application is not responsible for the projection update, it never knows
  the current projection state, which is then eventually consistent. This might be confusing, especially during a
  read-after-write scenario, where the user expects to see the result of the update immediately after the command is
  executed.

- **Maintainability**: in terms of maintainability, a Subscribed Projection is similar to a Managed Projection, as it
  allows to change the business logic of the event handlers, and change the underlying structure the derived views. To
  do so, it is necessary to update the serial of the projection every time the projection class is changed - check out
  the [documentation]({{< ref "/usage/factus/projections/snapshotting#serials">}}). The full event-stream will then be
  re-consumed on the next catch-up phase (when the new projection starts), to rebuild the derived views.

### Local Subscribed Projection

[Local Subscribed Projection documentation]({{< ref "/usage/factus/projections/types/local-subscribed-projection">}})

- **Scalability**: same considerations made for the Local Managed Projection apply.

- **Performance**: same considerations made for the Local Managed Projection apply.

- **Query flexibility**: same considerations made for the Local Managed Projection apply.

- **Complexity**: same considerations made for the Local Managed Projection apply.

- **Data consistency**: same considerations made for the Local Managed Projection apply, with the difference that since
  the application is not responsible for the projection update, it never knows the current projection state, which is
  then eventually consistent.

- **Maintainability**: same considerations made for the Local Managed Projection apply.

---

## Selecting the Right Projection Type

When embarking on the journey of selecting the right projection type for your event sourcing project, it is crucial to
carefully evaluate and prioritize the identified requirements based on your project's unique context.

That being said, here are some general Q&As to help you make an informed choice:

**Q: Are you still modelling for a prototype?**

A: If yes, then you can start with a Local, as it's the easiest and most intuitive projection to
implement and quick to change an rebuild.

**Q: Do you need to easily rebuild your projection?**

A: If yes, then consider using a Local Projection, as it allows to rebuild the in-memory derived views from scratch by
simply restarting the application. Consider anyway that this might have an impact on the overall application
performance, as this will produce an overhead on each deployment.

**Q: Do you need to ensure high availability for queries of a usecase, even when the Event Store is unavailable?**

A: If yes, then make sure to go for a projection that doesn't rely on the Event Store for persisting its state. Consider
the trade-offs between local and externalized states:

- Local states are faster to query, easier to implement and to maintain, but they need to be rebuilt from scratch on
  every restart
- Externalized states are harder to implement and maintain, but they can be rebuilt incrementally, and are available
  across multiple instances

**Q: Does your query need to ensure read-after-write consistency?**

A: If yes, then it's suggested to choose a projection that can be updated synchronously, like a SnapshotProjection, an
Aggregate or a ManagedProjection. Depending on the amount of data to be read, and the persistence layer, this might have
a different impact on the application performance.

**Q: Should the projected data be available for external services?**

A: If yes, then opt for a projection that offers freedom in terms of persistence, like a ManagedProjection or a
SubscribedProjection. This will allow to store the derived views in an external datasource, and to query them using
whatever technology is available.

**Q: Does a specific query need a single entity or object?**

A: If yes, then you can opt to use a dedicated Aggregate for the query. Generally speaking, Aggregates are usually fast,
easier to implement and to maintain, but they might be not suitable for very complex queries that require to aggregate
multiple event types. You can still use different projection types for different queries, and combine them together in
your application.

---

## Best Practices and Tips

Check the following [guide](https://docs.factcast.org/usage/factus/tips/) regularly, as it is updated with tips to
improve performance, or fix common issues.

When using Snapshot Projections, consider using the [factcast-snapshotcache-redisson](https://docs.factcast.org/usage/factus/setup/#redis-snapshotcache)
module, to store the snapshots in a Redis cluster, instead of the Event Store.
This will reduce the load on the Event Store, and will allow to scale the snapshots cache independently.
