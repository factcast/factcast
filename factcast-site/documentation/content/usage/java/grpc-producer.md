+++
draft = false
title = "Java GRPC Producer"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "usage"
identifier = "java_grpc_producer"
weight = 10

+++

## Java GRPC Producer

#### FactCast.publish(List&lt;Fact&gt; factsToPublish)

In order to produce Facts, you need to create Fact Instances and publish them via ```FactCast.publish()```.

On method return, the publishing of all the Facts passed to ```FactCast.publish()``` as a parameter are expected to have been written to PostgreSQL sucessfully. The order of the Facts is preserved while inserting into the database. All the inserts are done in a transactional context, so that the atomicity is preserved.

If the method returns exceptionally, of the process is killed or interrupted in any way, you cannot know if the Facts have been sucessfully written. In that case, just repeat the call: if the write had gone through you'll get and exception complaining about duplicate IDs, if not – you may have a chance to succeed now.

#### FactCast.publish(Fact toPublish)

acts the same way, than the List counterparts above, just for a List of one Fact.

## Example Code

Here is some example code assuming you use the Spring GRPC Client:

```java
@Component
class Foo{
 @Autowired
 FactCast fc;

 public void someMethod(){
   fc.publish( new SomethingHappenedFact() );
 }
}
```


