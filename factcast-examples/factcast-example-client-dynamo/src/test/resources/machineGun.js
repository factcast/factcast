const uuidV4 = require('uuid/v4');
const grpc = require('grpc');
const protoDescriptor = grpc.load('/home/usr/work/workspaces/github/factcast/factcast-grpc-api/src/main/proto/FactStore.proto');
const RemoteFactStore = protoDescriptor.org.factcast.grpc.api.gen.RemoteFactStore;
const store = new RemoteFactStore('localhost:9090', grpc.credentials.createInsecure());

setInterval(() =>
  store.publish([{
    header: JSON.stringify({
      id: uuidV4(),
      ns: 'my-cool-fact-ns'
    }),
    payload: JSON.stringify({
      foo: Date.now()
    })
  }], (err, feature) => {
    if (err) {
      console.log(err)
    }
  }), 1);
