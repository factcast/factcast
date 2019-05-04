+++
draft = false
title = "Spring Boot Server fat-jar (TLS)"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "setup"
identifier = "fatjar-tls"
weight = 120

+++

#### Non-Encrpyted

In order to run a simple example Factcast server, you could enter the project **factcast-examples/factcast-example-server**, see [fatjar](../fatjar)


## TLS Server

There is an extra example project that demonstrates the usage of TLS for your server that can be found **factcast-example-tls-server**

We tried to stick as close as possible to what we have in **factcast-examples/factcast-example-server** to demonstrate the necessary changes and nothing more.

Obviously, for running a TLS Server, you need a certificate. We packaged a snakeoil localhost certificate for you to test. This cert can be found in **src/etc/certificates/**. In order to create your own selfsigned certificate, there is a shell script you can use as a starting point.

#### obvisouly, you should use proper trusted certificates when you run FactCast in production - you have been warned

In order to run the TLS Server, go to **factcast-examples/factcast-example-tls-server** and run

```sh
mvn spring-boot:run
```

[{{<icon name="circle-arrow-right" size="small">}}Read more on Ports]({{%relref "/setup/examples/ports.md"%}})
