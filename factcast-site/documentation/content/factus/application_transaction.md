+++
draft = false
title = "Transactional Event Application"
description = ""


creatordisplayname = "Maik Töpfer"
creatoremail = "maik.toepfer@prisma-capacity.eu"


parent = "factus-projections"
identifier = "factus-transactional-event-application"
weight = 1020

+++

When processing events, a projection has two tasks:
1. persist the changes resulting from the fact 
2. store the current fact stream's position 

When using an external datastore (e.g. Redis), Factus needs to ensure that these two tasks happen atomically:  
either both tasks are executed or none. This prevents corrupted data in case e.g. the Redis cluster goes down in the wrong moment.  

{{<mermaid>}}
sequenceDiagram
    participant Projection
    participant External Data Store
    Projection->>External Data Store: 1) update projection
    Note right of External Data Store: Inside Transaction
    Projection->>External Data Store: 2) store Fact stream position
{{</mermaid>}}
*Changes to the external datastore run in a transaction* 

To enable atomic writes, Factus supports transactions for the following external data stores:
- Redis
- Postgres
