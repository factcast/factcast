+++
draft = false
title = "Redis Transactional Projection"
description = ""

creatordisplayname = "Maik Töpfer"
creatoremail = "maik.toepfer@prisma-capacity.eu"

parent = "factus-projections"
identifier = "redis-transactional-projections"
weight = 1021
+++

A *Redis transactional projection* is a [transactional projection]({{<ref "transactional_projections">}}) which provides classic transactionality  
employing a [Redission RTransaction](https://www.javadoc.io/doc/org.redisson/redisson/latest/org/redisson/api/RTransaction.html).    

Working with a *Redis transactional projection* is **synchronous**. To ensure permanent data consistency, the Redission client 
constantly communicates with the Redis server. 

For this reason a *Redis transactional projection* is best used for projections which
- handle only a few events and 
- which need to read the projection data during the handling of an event. 
 
For a more performant alternative see [Redis batch projection]({{<ref "redis-batch-projection.md">}})

#####################################################




- Leaner then Spring Transactional Projections. 
- Works directly with Tx and Batch

when working with a Redis Transaction Projection, Factus accepts additional arguments...

tx.getBucket(key) 
-> fresh bucket per call
-> expensive - network I/O

Redis Batch
- alternative to Redis Transaction
- batch.getBucket() -> async bucket

? setAsync multiple time -> is assumed to be in order 


tx.getBucket(...).set()
- always I/O operation
- sync

batch.getBucket(...).setAsync(...)
- defered I/O operation
- async
- non-blocking

recommendation:
- only a few events and reading during handling of events -> Redisson TX
- many events no reading during handling -> Redission batch


Docs Redis Batch:
Multiple commands can be sent in a batch using RBatch object in a single network call. 
Command batches allows to reduce the overall execution time of a group of commands. 
In Redis this approach called Pipelining. Follow options could be supplied during object creation:

Docs Redis Transaction:
Redisson objects like RMap, RMapCache, RLocalCachedMap, RSet, RSetCache and RBucket could participant in Transaction with ACID properties. 
It uses locks for write operations and maintains data modification operations list till the commit() operation. 
On rollback() operation all aquired locks are released. Implementation throws org.redisson.transaction.TransactionException 
in case of any error during commit/rollback execution.

------------------------------

- Question:
  example based on fastPut/ fastRemove  ?

- possible performance improvements:
       tx.getMap(redisKey()).fastRemove(deleted.aggregateId()); 
       instead of
       tx.getMap(redisKey()).remove(deleted.aggregateId()); 
