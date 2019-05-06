+++
draft = false
title = "Ports"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "setup"
identifier = "ports"
weight = 500

+++

## Port defaults and how to change them

The default TCP-Port exposed is 9090. As usual you can set it via environment variables.

Standard ports used:

|Port|Protocol|Component|Property|
|:--|:--|:--|:--|
|9090|HTTP2|factcast-server-grpc|grpc.server.port (for the bind address: grpc.server.host, defaults to 0.0.0.0) |
