+++
draft = false
title = "Testing"
description = ""
date = "2017-04-24T18:36:24+02:00"
weight = 99

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"

[menu.main]
parent = "usage"
identifier = "testing"

+++

Factcast comes with a module 'factcast-test' that includes a Junit5 extension that you can use to wipe the postgres database clean between integration tests.
The idea is, that in integration tests, you may want to start every test method with no preexisting events.
Assuming you use the excellent TestContainers library in order to create & manage a postgres database in integration tests, the extension will find it and wipe it clean.
In order to use the extension you either need to enable [Junit-Extension-Autodetection](https://junit.org/junit5/docs/current/user-guide/#running-tests-build-maven-config-params), or use
```java
@ExtendWith(FactCastExtension.class)
``` 
on your integration Test Class.

The easy way to get the full package is to just extend AbstractIntegrationTest:

```java
public class MyIntegrationTest extends AbstractFactcastIntegrationTest { // ...
} 
```
which gives you the latest factcast found locally or at docker-hub running against a somewhat current postgres, both being started in a docker container (locally installed docker is a prerequiste of course).

Also, in order to make sure, that FactCast-Server is **NOT** caching internally in memory, you can add a property to switch it into integrationTestMode. 
[See Properties](/setup/properties).

  
