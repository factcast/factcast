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


Properties you can use to configure FactCast:

### Schemaregistry

| Property        | Description           | Default   
| ------------- |:-------------|:-----|
| factcast.store.pgsql.schemaRegistryUrl            | if a schemaRegistryUrl is defined, FactCast goes into validating mode. The only protocols allowed here are *'http', 'https', 'classpath' and 'file'. Note that http(s) and file always require two slashes after the colon, e.g. 'https://someserver/...' or 'file:///root/folder/...'.*|  
| factcast.store.pgsql.persistentRegistry           | if fetched Schema and Transformation Documents are persisted into Postgres | false 
| factcast.store.pgsql.allowUnvalidatedPublish      | If validation is enabled, this controls if publishing facts, that are **not validatable** (due to missing meta-data or due to missing schema in the registry) are allowed to be published or should be rejected.  |  false 
| factcast.store.pgsql.schemaStoreRefreshCron       | defines the cron schedule for refreshing the SchemaRegistry by querying for the latest remote changes | `*/60 * * * * *` (every minute) |
| factcast.store.pgsql.allowSchemaReplace|If a schema can be replaced by an updated version from the registry (not a good idea in production environments)|false

---

### Transformation-Registry

| Property        | Description           | Default   
| ------------- |:-------------|:-----|
| factcast.store.pgsql.persistentTransformationCache                    | if Transformed Fact payloads are persistently cached into Postgres| false 
| factcast.store.pgsql.inMemTransformationCacheCapacity                 | when using the inmem impl of the transformation cache, this is the max number of entries cached. The minimum value here is 1000. | 1_000_000 
| factcast.store.pgsql.deleteTransformationsStaleForDays                | when using the persistent impl of the transformation cache, this is the min number of days a transformation result is not read in order to be considered stale. This should free some space in a regular cleanup job | 14  
| factcast.store.pgsql.transformationCacheCompactCron                   | defines the cron schedule for compacting the transformation result cache | `0 0 0 * * *` (at midnight)

---

### Performance / Reliability

| Property        | Description           | Default   
| ------------- |:-------------|:-----|
|factcast.store.pgsql.factNotificationBlockingWaitTimeInMillis| Controls how long to block waiting for new notifications from the database (Postgres LISTEN/ NOTIFY mechanism). When this time exceeds the notifications is repeated | 15000 (15sec)
|factcast.store.pgsql.factNotificationMaxRoundTripLatencyInMillis| When Factcast did not receive any notifications after factNotificationBlockingWaitTimeInMillis milliseconds it validates the health of the database connection. For this purpose it sends an internal notification to the database and waits for the given time to receive back an answer. If the time is exceeded the database connection is renewed | 200
|factcast.store.pgsql.factNotificationNewConnectionWaitTimeInMillis| how much time to wait between invalidating and acquiring a new connection. note: This parameter is only applied in the part of Factcast which deals with receiving and forwarding database notifications | 100
|factcast.store.pgsql.page-size| How many Facts to fetch from the database in one go. Higher values mean more memory usage. | 50
|factcast.store.pgsql.catchup-strategy| FETCHING uses database cursors where PAGED uses separate queries on TEMPORARY tables. FETCHING tends to be faster. | FETCHING


___

### Snapshots

| Property        | Description           | Default   
| ------------- |:-------------|:-----|
| factcast.store.pgsql.deleteSnapshotStaleForDays |   min number of days a snapshot is kept even though it is not read anymore | 90  
| factcast.store.pgsql.snapshotCacheCompactCron             |defines the cron schedule for compacting the snapshot cache | `0 0 0 * * *` (at midnight)

___

### gRPC

Properties you can use to configure gRPC:


#### gRPC Client

| Property        | Description           | Default  | Example |
| ------------- |:-------------|:-----|:-----|
|grpc.client.factstore.credentials|Credentials in the form of username:secret|none|myUserName:mySecretPassword
|grpc.client.factstore.address| the address(es) fo the factcast server| none |static://localhost:9090 |
|grpc.client.factstore.negotiationType| Usage of TLS or Plaintext? | TLS | PLAINTEXT |
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

#### FactCast client specific

| Property        | Description           | Default  | Example |
| ------------- |:-------------|:-----|:-----|
|factcast.grpc.client.catchup-batchsize|Request a batchsize in catchup phase. Produces larger message and better compression. Remember that this setting increases the memory requirements, as well as the individual message size so depending on you Fact-payload size, and this setting, you may want to increase the allowed max-in/out limits of GRPC (defaulting to ~4mb per message). Our tests have shown that values >100 seem to have an insignificant impact - your mileage may vary. Setting is valid since 0.3.9.|50|100
|factcast.grpc.client.enable-fast-forward|If the server supports it, enables fast forwarding. This is supposed to speedup frequent queries that cluster around the end of the global Fact-Stream and thus can use dedicated temporary rolling indexes.|true|false

#### grpc Server

| Property        | Description           | Default  | Example |
| ------------- |:-------------|:-----|:-----|
|`grpc.server.permit-keep-alive-without-calls` | Configures whether clients are allowed to send keep-alive HTTP/2 PINGs even if there are no outstanding RPCs on the connection| false | true |
|`grpc.server.permit-keep-alive-time`          | Specifies the most aggressive keep-alive time in seconds clients are permitted to configure. Defaults to 5min. | 300 | 100 |
|`factcast.grpc.bandwith.numberOfFollowRequestsAllowedPerClientPerMinute` | after the given number of follow requests from the same client per minute, subscriptions are rejected with RESOURCE_EXHAUSTED | 5 | 5|
|`factcast.grpc.bandwith.initialNumberOfFollowRequestsAllowedPerClient` | ramp-up to compensate for client startup| 50 | 50 |
|`factcast.grpc.bandwith.numberOfCatchupRequestsAllowedPerClientPerMinute` | after the given number of catchup requests from the same client per minute, subscriptions are rejected with RESOURCE_EXHAUSTED| 6000 | 6000 |
|`factcast.grpc.bandwith.initialNumberOfCatchupRequestsAllowedPerClient` | ramp-up to compensate for client startup |36000 | 36000 |
|`factcast.grpc.bandwith.disabled` | completely disables checking if set to true | false | true |

#### gRPC Server recommended settings

```
grpc.server.permit-keep-alive-without-calls=true
grpc.server.permit-keep-alive-time=100
```


### Testing

| Property        | Semantics           | Default   
| ------------- |:-------------|:-----|
|factcast.store.pgsql.integrationTestMode| when set to true, disables all non-essential memory-internal caches, timing might differ to production of course. | false

Further details can be found here : `net.devh.boot.grpc.server.config.GrpcServerProperties`. 
