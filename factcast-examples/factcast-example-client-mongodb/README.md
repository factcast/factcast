### factcast-example-client-dynamo

example of how to use factcast-client-grpc with a mongoDb projection

1. run local mongoDb with `docker run -d -p 27017:27017 --name mongodb mongo`
   1. To execute transactional examples a replica set is required. You can startup the mongoDb setup defined in `docker-compose.yml` by running `docker compose up -d`
   2. Afterwards initiate the replica set with: `docker compose exec mongodb mongosh --eval "rs.initiate()"`
2. run example server from `factcast-example-server`
3. start `ExampleMongoDbClient`