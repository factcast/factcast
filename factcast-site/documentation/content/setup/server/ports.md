+++
draft = false
title = "Ports"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "setup_server"
identifier = "ports"
weight = 50

+++

### DEPRECATED Documentation. Will be updated https://github.com/Mercateo/factcast/issues/312


## Port defaults and how to change them

The default TCP-Ports exposed are 8080 and 9090. As usual you can set them via environment variables.

Standard ports used:

|Port|Protocol|Component|Property|
|:--|:--|:--|:--|
|8080|HTTP|Spring Management API|management.port (should *really* be changed)|
|8080|HTTP|factcast-server-rest|server.port|
|9090|HTTP2|factcast-server-grpc|grpc.server.port (for the bind address: grpc.server.host, defaults to 0.0.0.0) |
