---

title: "Transformation"
weight: 90
type: docs
----------

## Stay compatible

When decoupling services via facts, it is vitally important, that the consuming party understands the facts it is interested in. Therefore, evolution is a challenge. As soon, as the publisher starts publishing a particular fact type in a (non-compatible) format, the consumer will break. This leads to complex deployment dependencies, that we tried to avoid in the first place.

In order to avoid this, the most important advice is:

> make sure, new fact versions are always downwards compatible

and

> make sure you tolerate unknown properties when processing facts

If there are only additions for instance in the new fact version, then the 'tolerant reader' can kick in and ignore unknown properties. See [Tolerant Reader](https://www.martinfowler.com/bliki/TolerantReader.html)

Sometimes however, you need to change a fact schema in terms of structure. We assume here, you use a Schema/Transformation registry, as this feature is disabled otherwise.

## Downcast

In the above scenario, the publisher wants to start publishing facts with the updated structure (version 2) while the consumer that expects the agreed upon structure (version 1) should continue to work.

For this to work, there are three prerequisites:

1. The publisher needs to communicate what version he wants to publish

This would not work otherwise, because we assume version 1 and version 2 to be incompatible, so the correct schema must be chosen for validation anyway.
In this case, it would be "version 2".

2. The consumer must express his expectation

When it subscribes on a particular fact type, it also needs to provide the version it expects ("version 1" here)

3. A transformation code is available in the registry that can do the transformation if needed.

The Registry takes little javascript snippets, that can convert for instance a version 2 fact payload, into a version 1.

Factcast will build transformation chains if necessary (from 4-3, 3-2 and 2-1, in order to transform from version 4 to version 1). Every non-existent transformation is assumed compatible (so no transformation is necessary).

When necessary, you also can add a 4-1 transformation to the registry to do the transformation in one step, if needed. Beware though, you will not benefit much in terms of performance from this.

{{% alert title="Transformation rules" %}}

- If there are many possible paths to transform from an origin version to a specific target version, the **shortest always wins**. If there are two equally long paths, the one that _uses the bigger shortcut sooner_ wins.
- A consumer also can be able to handle different versions for a particular fact type. In this case – again – the **shortest path wins**. If there is a tie, _the higher target version wins_.

{{% /alert %}}

## Upcast

Anther use-case is that, over time, the publisher published 3 different versions of a particular fact type, and you (as a consumer) want to get rid of the compatibility code dealing with the older versions.

Same as downcast, just express your expectation by providing a version to your subscription, and factcast will transform all facts into this version using the necessary transformations from the registry.
While for downcast, missing transformations are considered compatible, upcasting will fail if there is no transformation code to the requested version.

In terms of transformation priorities: the same rules as in downcasting apply.

_If transformation is not possible due to missing required code snippets in the registry or due to other errors, FactCast will throw an exception_.

## Caching

Obviously, transformation via javascript from a VM brings a considerable overhead. (Might be better with graal, which is not yet supported)

In order not to do unnecessary work, factcast will cache the transformation results, either in memory or persistently.

See the [Properties](/setup/properties)-Section on how to configure this.

**Note:** Whenever a transformation is not possible, factcast will just throw an appropriate exception.

For an example, see [the example registry](https://github.com/factcast/factcast/tree/master/factcast-examples/factcast-example-server/src/main/resources)

##### please consider using the schema registry cli tool, rather than trying to manually fiddle with the registry

Remember that problems in the registry can cause errors at runtime in factcast, so that you should validate the syntactical correctness of it. This is where the cli tool will help.
