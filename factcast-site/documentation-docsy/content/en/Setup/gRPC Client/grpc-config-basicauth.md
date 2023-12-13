---
title: "gRPC BasicAuth"
type: docs
weight: 100
---

### Using BasicAuth from a client

From a client's perspective, all you need to do is to provide credentials. Once the credentials are configured, they are
used on every request in a Basic-Auth fashion (added header to request).

In order to define credentials, just set the appropriate property to a value of the format 'username:password', just as
you would type them into your browser when a basic-auth popup appears.

```
# if this property is set with a value of the format 'username:password', basicauth will be used.
grpc.client.factstore.credentials=myUserName:mySecretPassword
```

You can always use environment variables or a `-D` switch in order to inject the credentials.

see
module [examples/factcast-example-client-basicauth](https://github.com/factcast/factcast/tree/master/factcast-examples/factcast-example-client-basicauth)
for an example
