+++
draft = false
title = "nodeJS GRPC Producer"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "usage"
identifier = "js_grpc_producer"
weight = 100

+++

## nodeJS GRPC Producer

Producing Facts via nodeJS is very simple due to the available [gRPC](https://www.npmjs.com/package/grpc) NPM Module. It will generate a stub constructor called ```RemoteFactStore``` from our proto file.

```javascript
const uuidV4 = require('uuid/v4');
const grpc = require('grpc');
const protoDescriptor = grpc.load('./FactStore.proto');
const RemoteFactStore = protoDescriptor.org.factcast.grpc.api.gen.RemoteFactStore;

// store allows us to publish, subscribe and fetchById (see proto file)
const store = new RemoteFactStore('localhost:9090', grpc.credentials.createInsecure());

store.publish([{
  header: JSON.stringify({
    id: uuidV4(),
    ns: 'myapp'
  }),
  payload: JSON.stringify({
    foo: Date.now()
  })
}], (err, feature) => {
  if (err) {
    console.log(err)
  }
})
```
See the [Facts]({{%relref "/concept/fact.md"%}}) page for detailed information about all possible and required header fields.
