---
title: "Compression"
type: docs
weight: 155
description: Selecting a compressor
---

## Why ?

Wherever there is network communication, the question of compression comes up. The FactCast server currently supports
three compressors out of the box:

- LZ4
- Snappy
- GZip

Unfortunately, GRPC does not support stream-compression, but only message-compression., This means that the
efficiency of the compression is dependent on the message size. We'll get to that...

## Client chooses

In order to agree on which compressor to use, there is an initial handshake when the client connects to the server,
in which the available compressors on client and server are compared, and the server selects the one to use.

The server send a list of what he accepts, and the client picks his _favorite_ compressor out of that list in the order
shown above (LZ4 first, then snappy, GZip as a fallback).

As the client should be low on dependencies and assumptions, Gzip (as supported by the JDK) is the default compressor
every client supports.

In order to prefer snappy or LZ4, you'd need to add one or both of the following dependencies (or later versions)
to your client. Once they are on the classpath, the client will pick them up automatically, and the server will
prefer them over GZip.

{{< cardpane >}}
{{< card header="Snappy" >}}

```xml
<dependency>
  <groupId>org.xerial.snappy</groupId>
  <artifactId>snappy-java</artifactId>
  <version>1.1.8.4</version>
</dependency>
```

{{< /card >}}
{{< card header="LZ4" >}}

```xml
<dependency>
  <groupId>net.jpountz.lz4</groupId>
  <artifactId>lz4</artifactId>
  <version>1.3.0</version>
</dependency>
```

{{< /card >}}
{{< /cardpane >}}

## Compressor efficiency

As there currently is no stream-compression in GRPC, the server compresses each message transferred to the client separately.
The smaller this message is, the less efficient the compression can be. For this reason it is important (during the
catchup phase, where you expect a lot of messages) to allow the server to bundle messages into a batch that it will
compress and send as one message.

See [`factcast.grpc.client.catchup-batchsize`](/setup/properties/#factcast-client-specific)

In the follow phase, this setting has no meaning, as you don't want to wait for your batch to fill up before you receive
the latest publications from the server. Latency is more important than compression efficiency in that case.
