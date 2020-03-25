+++
draft = false
title = "Properties"
description = ""
date = "2017-04-24T18:36:24+02:00"
weight = 210

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"

[menu.main]
parent = "usage"
identifier = "properties"

+++


Properties you can use to configure Factcast:

### Schema/Transformation-Registry

| Property-Name        | Semantics           | Default  | Example |
| ------------- |:-------------|:-----|:-----|
| factcast.store.pgsql.schemaRegistryUrl      | if a schemaRegistryUrl is defined, FactCast goes into validating mode. The only protocols allowed here are *'http', 'https' and 'classpath'*| none | classpath:my-registry|
| factcast.store.pgsql.persistentRegistry      | if fetched Schema and Transformation Documents are persisted into Postgres | false |
| factcast.store.pgsql.persistentTransformationCache      | if Transformed Fact payloads are persistently cached into Postgres| false |
| factcast.store.pgsql.allowUnvalidatedPublish      | If validation is enabled, this controls if publishing facts, that are **not validatable** (due to missing meta-data or due to missing schema in the registry) are allowed to be published or should be rejected.  |  false |
| factcast.store.pgsql.persistentSchemaStore |   If validation is enabled, this controls if a local snapshot of the schemaregistry is persisted to psql or just kept in mem.   |    true |
| factcast.store.pgsql.schemaStoreRefreshRateInMilliseconds | defines the time in milliseconds that FactCast pauses between checking for a change in the SchemaRegistry      |    15000 |
| factcast.store.pgsql.inMemTransformationCacheCapacity |  when using the inmem impl of the transformation cache, this is the max number of entries cached. The minimum values here is 1000. | 1_000_000 | 1000 |
| factcast.store.pgsql.deleteTransformationsStaleForDays |  when using the persistent impl of the transformation cache, this is the min number of days a transformation result is not read in order to be considered stale. This should free some space in a regular cleanup job | 14 | 30 |



___

## gRPC

Properties you can use to configure gRPC:


#### gRPC Client

| Property-Name        | Semantics           | Default  | Example |
| ------------- |:-------------|:-----|:-----|
|grpc.client.factstore.credentials|Credentials in the form of username:secret|none|myUserName:mySecretPassword
|grpc.client.factcast.address| the address(es) fo the factcast server| none |static://localhost:9090 |
|grpc.client.factcast.negotiationType| Usage of TLS or Plaintext? | TLS | PLAINTEXT |
|grpc.client.factstore.enable-keep-alive| Configures whether keepAlive should be enabled. Recommended for long running (follow) subscriptions | false | true|
|grpc.client.factstore.keep-alive-time|The default delay before sending keepAlives. Defaults to 60s. Please note that shorter intervals increase the network burden for the server.||300|
|grpc.client.factstore.keep-alive-without-calls|Configures whether keepAlive will be performed when there are no outstanding RPCs on a connection.|false|true

#### gRPC Client recommended settings

```
grpc.client.factstore.enable-keep-alive=true
grpc.client.factstore.keep-alive-time=300
grpc.client.factstore.keep-alive-without-calls=true
```

Further details can be found here : `net.devh.boot.grpc.client.config.GrpcChannelProperties`.

#### grpc Server

|Property|Description|Proposed value|
|:--|:--|:--|
|`grpc.server.permit-keep-alive-without-calls`|  Configures whether clients are allowed to send keep-alive HTTP/2 PINGs even if there are no outstanding RPCs on the connection. Defaults to false.| true |
|`grpc.server.permit-keep-alive-time`          | Specifies the most aggressive keep-alive time in seconds clients are permitted to configure. Defaults to 5min. | 100 |

#### gRPC Server recommended settings

```
grpc.server.permit-keep-alive-without-calls=true
grpc.server.permit-keep-alive-time=100
```

Further details can be found here : `net.devh.boot.grpc.server.config.GrpcServerProperties`. 
