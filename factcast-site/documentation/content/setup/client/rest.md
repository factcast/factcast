+++
draft = false
title = "Spring Boot via REST"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "setup_client"
identifier = "boot_rest"
weight = 10

+++

## Using FactCast client in Spring boot via REST

When it comes to REST, there are basically two camps:

* The RESTafaris, that claim that *RESTful* without HATEOAS is not even REST, but rather JSON via HTTP
* The others that use *RESTful* as a means of RPC.

Without going into which ones are right or wrong, here are two (out of many) options of how to use the FactCast REST API.

### HateOAS client

One has to import the recent version of a suitable HATEOAS-Client, e.g. [this one](https://github.com/Mercateo/rest-hateoas-client). There are intentionally no beans for facts and subscriptions on the client side, because the user has the opportunity to code his own beans, which will be in the most times projections of the full beans, described by the schemas in the [REST-API documentation]({{%relref "/usage/rest/api-guide.adoc"%}}). In the [usage]({{%relref "/usage/overview.md"%}}) section there will be some example code for further reading.

### Feign client

{{%alert danger%}} TODO{{% /alert%}}


