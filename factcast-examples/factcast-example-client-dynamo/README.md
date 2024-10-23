### factcast-example-client-dynamo

example of how to use factcast-client-grpc with a dynamoDb projection

1. run local dynamo with `docker run --rm -p 8000:8000 amazon/dynamodb-local`
2. run example server from `factcast-example-server`
3. start `ExampleDynamoClient`