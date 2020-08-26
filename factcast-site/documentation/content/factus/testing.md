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

You can get the full package by just extending AbstractIntegrationTest:

```java
public class MyIntegrationTest extends AbstractFactcastIntegrationTest { // ...
} 
```

Also, in order to make sure, that FactCast-Server is **NOT** caching internally in memory, you can add a property to switch it into integrationTestMode. [See Properties](/setup/properties).

  
