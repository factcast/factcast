+++
draft = true
title = "Spring Boot & TLS"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "setup_server"
identifier = "fatjar-tls"
weight = 10

+++

#### Non-Encrpyted

In order to run a simple example Factcast Server, you could enter the project **factcast-examples/factcast-example-server]**, see [fatjar]


## TLS Server

There is an extra example project that demonstrated to usage of TLS for your server that can be found **factcast-example-tls-server**

We tried to stick as close as possible to what we have in **factcast-examples/factcast-example-server]** to demonstrate the necessary changes and nothing more.

Obviously, for running a TLS Server, you need a certificate.

[{{%icon circle-arrow-right%}}Read more on Ports]({{%relref "/setup/server/ports.md"%}})
