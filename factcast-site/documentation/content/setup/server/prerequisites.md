+++
draft = false
title = "Prerequisites"
description = ""
date = "2019-04-24018:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "setup_server"
identifier = "prerequisites"
weight = 50

+++



## Postgres

In Order to run the factcast server, you have to provide a Postgres database at least in version 9.4. 


#### UUID-OSSP

Additionally the Factcast needs the Postgres module [uuid-ossp](https://www.postgresql.org/docs/11/uuid-ossp.html). 
There are two possibilities to provide that module:

**1. Let the Factcast itself take care of installing that module**  
Unfortunately, the postgres user provided to the Factcast must be superuser. It's necessary at least for the first start with a fresh database. You can and should change the privileges after the first startup.

**2. Install the module manually**  
Login into your postgres console and execute the following command as superuser:

```
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

