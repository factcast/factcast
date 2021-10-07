~~---
title: "Hitchhiker's Guide To Building a Projector Plugin"
type: docs
weigth : 20
---

## Concept


## Definitions

Introduction in the building blocks of the projector plugin system

As an example the `factcast-factus-spring-tx` projector plugin is used.


### Projector

The `Projector` is the part of Factus which is responsible for handling the facts received from the fact subscription.
It provides the `@Handler` or `@HandlerFor` methods of a projection with the matching fact or event plus further [handler parameters](TODO link)  
If a handler method requires an instance of `EventObject` instead of a `Fact`, the `Projector` takes care of the conversion. 

The `Projector` is aware of all registered `ProjectorLens`es and executes their methods at the right point in time.
For example, as the name already hints, the `Projector` will call the `beforeFactProcessing(...)` method of all lenses
before the fact is processed by the matching handler method of the projection.

On each new fact subscription (e.g. via `update` or `fetch`) a new `Projector` is created.

## Projector Lens

A `ProjectorLens` changes the `Projector`'s behaviour.  To do so, an implementation of `ProjectorLens` can supply
a handful of methods which are called by the `Projector` at the right time:

| Method Signature                                                     | Called When By The `Projector`          |
|----------------------------------------------------------------------|-----------------------------------------------------|
| `void beforeFactProcessing(Fact)`                                    | before a fact is handled          |
| `void afterFactProcessing(Fact)`                                     | after a fact is handled          |
| `void afterFactProcessingFailed(Fact, Throwable)`                    | when handling of a fact was not successful                 |
| `void onCatchup(Projection)`                                         | when the projection has finished catching up with past events |                                                                                                                   |
| `boolean skipStateUpdate()`                                          | right after a fact was processed. Here, the lens tells the `Projector` if the projection should update its factstream position  |

Usually a `ProjectorLens` contains state to e.g. keep track when a projection should update it's factstream position.
For this reason each invocation of Factus (e.g. a call to `factus.update(...)`) is based on newly instantiated `ProjectorLens`es.
Since keeping track of state during fact processing is a common usecase, 
Factus provides the [`AbstractTransactionalLens` class](TODO link) for this purpose. This abstract class is e.g. used by
the [`SpringTransactionalLens`](TODO link)

To register one or more `ProjectorLens`es to the `Projector`, a `ProjectorPlugin` is used.

## Projector Plugin

A `ProjectorPlugin` supplies the Factus projector with one or more `ProjectorLens`es.
It's [an interface](TODO link) which requires the method `Collection<ProjectorLens> lensFor(@NonNull Projection p)` 
to be implemented. The method receives an instance of `Projection` as input. If the plugin is aware of one or more fitting
`ProjectorLens` it returns a `Collection` of ready-to use `ProjectorLens` instances. Otherwise, an empty collection is returned.

### Plugin Discovery

To locate and load `ProjectorPlugin`s, the `Projector` relies on the Java [`ServiceLoader`](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/ServiceLoader.html)
mechanism. A projector plugin acts as a service provider by providing an implementation for the `ProjectorPlugin` interface.


----------------------------------------------------------------------------------------------------

TODO next
- mention suppying data store access from the projection to the plugin via interface
- mention config annotation and it's usage 

To be loaded by the `Projector` a projector plugin needs to place a  
The module needs a class implementing the `ProjectorPlugin` interface forces the implementation of the `lensFor(...)` method:
```java
Collection<ProjectorLens> lensFor(@NonNull Projection p);
```

This method receives a `Projection` and decides if the plugin contains a matching `ProjectorLens` for it. If this is 
the case then tje `lensFor` method acts as factory, instantiates the concrete implementation of `ProjectorLens` 
and returns it inside a `Collection`.


## Walk Through Spring Transactional Projector Plugin

```mermaid
classDiagram
classDiagram
class ProjectorLens {
    <<interface>>
    beforeFactProcessing(fact)
    afterFactProcessing(fact)   
    afterFactProcessingFailed(fact, throwable)
    onCatchup(projection)
    skipStateUpdate() boolean
    parameterTransformerFor(class) Function 
}

class AbstractTransactionalLens {
    <<abstract>>
    beforeFactProcessing(fact)
    afterFactProcessing(fact)   
    afterFactProcessingFailed(fact, throwable)
    onCatchup(projection)
    doFLush()*
    doClear()*
}

class SpringTransactionalLens {
    beforeFactProcessing(fact)
    parameterTransformerFor(class) Function 
    doFLush()
    doClear()
}

ProjectorLens <|-- AbstractTransactionalLens : implements
AbstractTransactionalLens <|-- SpringTransactionalLens : extends
```

AbstractTransactionalLens
- encapsulates batching logic
- provides logic to flush at the right time
- bulk processing
 
- concrete flushing mechanism is provided by child (doFlush())
- doClear() called when afterFactProcessingFailed
- part of `factcast-factus`


What is a Projector Lense Plugin system ?
- execute action
  - before
  - after
  - on error
...of a fact application

- most useful to execute an action which spans multiple facts
- use case for the intro of the Projector Lense Plugin system: provide transactionality and batching

What is a Projector Lense?
- register hook methods for actions at certain state of fact processing
- is state aware
- can decide to so some action e.g. after n facts (like committing)
- knows about the concept of state updates (link) and can communicate to Factus wether to do it or not (link performance savings)
- in the current implementation uses a dedicated Transaction/ BatchMgr for the details
- 


Projector
- in charge of handling received facts
- knows about lenses and takes fitting lenses into account 
- per projection subscription (update, fetch...) it is checked at runtime if 
  - the lense is in charge for this projection
  - correct configuration via annotation is supplied
- lensFor acts as factory and provides the readily initialized lense back to the projector


ProjectorPlugin
- provides n lenses (factory)
-  
- communicate which lense is 
- implement interface
- register lenses of this plugin via lensFor
- this is runtime! so annotations of 

Projection specific annotation (like @SpringTransactional)
- acts as marker that a projection should be considered
- contains configuration information which provides config for the lense


Lens vs. SpecificProjection
- they share responsibility
- projection  
  - knows what to do with the current fact eg update some externally hold state
  - knows how to update the factStreamPosition
  - knows how to aquire a write lock
- lense knows how to work with a bulk of facts
  - knows when to submit a bulk
  - knows when to write the factStream position
- 

## Projector Lens

A ProjectorLens is
- changes the original projector behaviour
- orchestrates projection

- Usually has state -> hence always freshly instantiated per
-

- follow phase: no bulk processing

- lense knows how to work with a bulk of facts
  - knows when to submit a bulk
  - knows when to write the factStream position

- register hook methods for actions at certain state of fact processing
- is state aware
- can decide to so some action e.g. after n facts (like committing)
- knows about the concept of state updates (link) and can communicate to Factus wether to do it or not (link performance savings)
- in the current implementation uses a dedicated Transaction/ BatchMgr for the details
-

TODO projector plugin (the project/ module) vs. `ProjectorPlugin` (the interface)
- dependency, artifact, library, "some jar", "maven project"
- or concrete SpringTxProjectorPlugin

TODO @SpringTransactional 1 --- 1 SpringTransactionalLens    ?


```
ProjectorPlugin (1) ------ (n) ProjectorLens 
```

TODO parameterTransformerFor mechanism

What do I need for a plugin

- marker+config annotation like SpringTransactional
- usually a specialized projection type like AbstractSpringTxProjection, e.g. provide the PlatformTransaction mgr to the lense
- a lense which contains the details of what should exactly happen before, the the point and after a fact is handled
- the projector plugin for service discovery and instanciation (factory)
- META-INF.services entry of the plugin


is required to use one of the supported service provider discovery mechanisms.

The simplest one is to announce the service provider via class path:

*A service provider that is packaged as a JAR file for the class path is identified by placing a provider-configuration file
in the resource directory META-INF/services. The name of the provider-configuration file is the fully qualified binary name of the service.
The provider-configuration file contains a list of fully qualified binary names of service providers, one per line.* [`ServiceLoader` docs](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/ServiceLoader.html)

In case of the p

