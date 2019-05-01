+++
draft = false
title = "nodeJS GRPC Consumer"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "usage"
identifier = "js_grpc_consumer"
weight = 12

+++

## nodeJS GRPC Consumer

```javascript
const grpc = require('grpc');
const protoDescriptor = grpc.load('./FactStore.proto');
const RemoteFactStore = protoDescriptor.org.factcast.grpc.api.gen.RemoteFactStore;

const store = new RemoteFactStore('localhost:9090', grpc.credentials.createInsecure());

const subscription = store.subscribe({
  json: JSON.stringify({
    continuous: true,
    specs: [
      {
        ns: 'myapp'
      }
    ]
  })
})

subscription.on('data', (fact) => {
  console.log(fact);
})
```
