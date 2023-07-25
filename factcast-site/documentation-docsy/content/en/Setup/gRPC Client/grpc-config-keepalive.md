---
title: "gRPC KeepAlive"
type: docs
weight: 170
---

## keep-alive settings

Here are some good settings for an initial configuration of a SpringBoot FactCast client/server setup in case you ran into gRPC related client server communication troubles.

- Sending keep-alive HTTP/2 PINGs on the connection is useful in case you are running on infrastructure that doesn't support configurable idle timeouts, and therefore closes connections.

- The proposed values are defining a scenario where the client sends keep-alive HTTP/2 PINGs every 300s and the server accepts this behavior without sending `GO_AWAY ENHANCE_YOUR_CALM` to the client. Please adapt to your specific needs.

### Client side

| Property                                         | Description                                                                                                                 | Recommended | Default |
|:-------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------|:------------|---------|
| `grpc.client.factstore.enable-keep-alive`        | Configures whether keepAlive should be enabled.                                                                             | true        | false   |
| `grpc.client.factstore.keep-alive-time`          | The default delay before sending keepAlives. Please note that shorter intervals increase the network burden for the server. | 300         | 60      |
| `grpc.client.factstore.keep-alive-without-calls` | Configures whether keepAlive will be performed when there are no outstanding RPCs on a connection.                          | true        | false   |

Further details can be found here : `net.devh.boot.grpc.client.config.GrpcChannelProperties`.

### Server side

| Property                                      | Description                                                                                                                     | Recommended | Default |
|:----------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------|:------------|---------|
| `grpc.server.permit-keep-alive-without-calls` | Configures whether clients are allowed to send keep-alive HTTP/2 PINGs even if there are no outstanding RPCs on the connection. | true        | false   |
| `grpc.server.permit-keep-alive-time`          | Specifies the most aggressive keep-alive time in seconds clients are permitted to configure.                                    | 100         | 300     |

Further details can be found here : `net.devh.boot.grpc.server.config.GrpcServerProperties`.
