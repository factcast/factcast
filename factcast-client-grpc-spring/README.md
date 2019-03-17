### factcast-client-grpc

Provides a client-local implementation of a FactStore that communicates with a Remote FactStore via GRPC.

Configuration in 'application.yml':

```
grpc:
  client:
    factstore:
      host:
        - ibm.com
        - localhost
      port: 
        - 7777
        - 9090
```
to connect and balance between ibm.com:7777 and localhost:9090.
  