### factcast-client-grpc

Provides a client-local implementation of a FactStore that communicates with a Remote FactStore via GRPC.

Configuration in 'application.properties':

```
spring.grpc.client.channels.factstore.address=static://localhost:9090,ibm.com:7777
```

to connect and balance between ibm.com:7777 and localhost:9090.
  