+++
draft = false
title = "GRPC Consumer"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "usage"
identifier = "grpc_consumer"
weight = 20

+++

## GRPC Comsumer

As mentioned [before]({{%relref "intro_02_design.md#read-subscribe"%}}), there are three main Use-Cases for subscribing to a Fact-Stream:

* Validation of Changes against a sctrictly consistent Model (Catchup)
* Creating and maintaining a Read-Model (Follow)
* Managing volatile cached data (Ephemeral)



## Example Code

Here is some example code assuming you use the Spring GRPC Client:

```java
@Component
class Foo{
 @Autowired
 FactCast fc;

 public void someMethod(){
   //TOOD
 }
}
```


