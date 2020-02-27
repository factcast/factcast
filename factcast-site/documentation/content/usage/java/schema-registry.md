+++
draft = false
title = "Schema validation and Registry"
description = ""
date = "2017-04-24T18:36:24+02:00"
weight = 210

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"

[menu.main]
parent = "usage"
identifier = "schema-registry"

+++


Since version 0.2.0, FactCast can be configured to validate Facts before publishing them. In order to do that, FactCast needs to have a Schema for the Namespace/Type/Version of a Fact, that is expected to live in a Schema-Registry. 
The Schema Registry is a static webiste, that is referenced by the property 'factcast.store.pgsql.schemaRegistryUrl'.
If no 'schemaRegistryUrl' is provided, validation is skipped and FactCast behaves just like before.

Given, there is a SchemaRegistry configured, FactCast will (on startup, and regularly) fetch an index and crawl updated information.

For that to work, the schema-registry must follow a certain structure and naming convention. To make validating and building this static website easier and convenient, there is a tool [factcast-schema-cli](../fc-schema-cli) you can use. It turns raw data files (Json-Schema, markdown, example json payloads) into a nice, browsable website as well as generating the files needed for FactCast to discover new schema on the fly.



Properties you can use to configure this features' behavior:

| Tables        | Semantics           | Default  |
| ------------- |:-------------|:-----|
| factcast.store.pgsql.schemaRegistryUrl      | if a schemaRegistryUrl is defined, FactCast goes into validating mode. | none |
| factcast.store.pgsql.allowUnvalidatedPublish      | If validation is enabled, this controls if publishing facts, that are not validatable (due to missing meta-data or due to missing schema in the registry) are allowed to be published or should be rejected.  |  false |
| factcast.store.pgsql.persistentSchemaStore |   If validation is enabled, this controls if a local snapshot of the schemaregistry is persisted to psql or just kept in mem.   |    true |
| factcast.store.pgsql.schemaStoreRefreshRateInMilliseconds | defines the time in milliseconds that FactCast pauses between checking for a change in the SchemaRegistry      |    15000 |

An example can be found [here](/example-registry/) which is generated from the module 'factcast-examples/factcast-example-schema-registry/'
