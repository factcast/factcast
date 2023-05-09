---

title: "Schema validation and Registry"
weight: 40

type: docs
----------

Since version 0.2.0, FactCast can be configured to validate Facts before publishing them. In order to do that, FactCast
needs to have a Schema for the Namespace/Type/Version of a Fact, that is expected to live in a Schema-Registry. The
Schema Registry is a static webiste, that is referenced by the property 'factcast.store.schemaRegistryUrl'. If no '
schemaRegistryUrl' is provided, validation is skipped and FactCast behaves just like before.

Given, there is a SchemaRegistry configured, FactCast will (on startup, and regularly) fetch an index and crawl updated
information.

For that to work, the schema-registry must follow a certain structure and naming convention. To make validating and
building this static website easier and convenient, there is a
tool [factcast-schema-cli](/usage/lowlevel/cli/fc-schema-cli) you can use. It turns raw data files (Json-Schema,
markdown, example json payloads) into a nice, browseable website as well as generating the files needed for FactCast to
discover new schema on the fly.

An example can be
found [here](https://github.com/factcast/factcast/tree/master/factcast-examples/factcast-example-server/src/main/resources)
which is generated from the module 'factcast-examples/factcast-example-schema-registry/'

See the [Properties](/setup/properties)-Section on how to configure this.
