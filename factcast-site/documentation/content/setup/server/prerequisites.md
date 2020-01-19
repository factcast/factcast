+++
draft = false
title = "Boot Server Prerequisites"
description = ""
date = "2019-04-24018:36:24+02:00"

creatordisplayname = "Andreas Klipp"
creatoremail = "andreas.klipp@mercateo.com"

weight = 50

[menu.main]
parent = "setup"
identifier = "prerequisites"


+++



## Postgres

In order to run the Factcast server, you have to provide a Postgres database at least in version 9.4.
The following example shows the configuration with one user.

```
spring.datasource.username="user" //that user has to be provided
spring.datasource.password="password"
```


The user has to be a superuser, as he will also install Postgres modules like [uuid-ossp](https://www.postgresql.org/docs/11/uuid-ossp.html).

### If you don't want to provide a superuser 

If you don't want to provide a superuser, you have to consider the following points:

1.) The database user needs at least the permission to query the pg_roles view. According to the [documentation](https://www.postgresql.org/docs/10/view-pg-roles.html) it's publicly accessible, so that shouldn't be a problem. 

2.) The Factcast needs the Postgres module [uuid-ossp](https://www.postgresql.org/docs/11/uuid-ossp.html). You have to install that module manually. The server will recognize the already installed module and it won't throw an error caused by missing privileges.
Login into your Postgres console and execute the following command as superuser:

```
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
``` 
