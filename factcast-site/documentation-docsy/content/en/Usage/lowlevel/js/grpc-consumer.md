+++
title = "nodeJS GRPC Consumer"
weight = 101
type="docs"
+++

```javascript
const grpc = require("grpc");
const protoDescriptor = grpc.load("./FactStore.proto");
const RemoteFactStore =
	protoDescriptor.org.factcast.grpc.api.gen.RemoteFactStore;

const store = new RemoteFactStore(
	"localhost:9090",
	grpc.credentials.createInsecure()
);

const subscription = store.subscribe({
	json: JSON.stringify({
		continuous: true,
		specs: [
			{
				ns: "myapp",
			},
		],
	}),
});

subscription.on("data", (fact) => {
	console.log(fact);
});
```
