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

| Property-Name        | Semantics           | Default   
| ------------- |:-------------|:-----|
| factcast.store.pgsql.deleteTransformationsStaleForDays    |  when using the persistent impl of the transformation cache, this is the min number of days a transformation result is not read in order to be considered stale. This should free some space in a regular cleanup job | 90  
| factcast.store.pgsql.snapshotCacheCompactCron             |defines the cron schedule for compacting the transformation result cache | `0 0 0 * * *` (at midnight)

---

### Snapshots

| Property-Name        | Semantics           | Default   
| ------------- |:-------------|:-----|
| factcast.store.pgsql.deleteSnapshotStaleForDays |  when using the persistent impl of the transformation cache, this is the min number of days a transformation result is not read in order to be considered stale. This should free some space in a regular cleanup job | 14  
| factcast.store.pgsql.transformationCacheCompactCron|defines the cron schedule for compacting the transformation result cache | `0 0 0 * * *` (at midnight)



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
|`factcast.grpc.bandwith.numberOfFollowRequestsAllowedPerClientPerMinute` | after the given number of follow requests from the same client per minute, subscriptions are rejected with RESOURCE_EXHAUSTED | 5 |
|`factcast.grpc.bandwith.initialNumberOfFollowRequestsAllowedPerClient` | ramp-up to compensate for client startup| 50 |
|`factcast.grpc.bandwith.numberOfCatchupRequestsAllowedPerClientPerMinute` | after the given number of catchup requests from the same client per minute, subscriptions are rejected with RESOURCE_EXHAUSTED| 6000 |
|`factcast.grpc.bandwith.initialNumberOfCatchupRequestsAllowedPerClient` | ramp-up to compensate for client startup | 36000 |


#### gRPC Server recommended settings

```
grpc.server.permit-keep-alive-without-calls=true
grpc.server.permit-keep-alive-time=100
```

Further details can be found here : `net.devh.boot.grpc.server.config.GrpcServerProperties`. 
