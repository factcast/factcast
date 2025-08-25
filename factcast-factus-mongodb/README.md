## Usage

Allows to build projections using MongoDB or AWS DocumentDB.

### Transactionality not supported

For now, no transactionality is supported.

## Prerequisites

In order to use this projection provide the following:

- MongoDB client
- The name of the mongo database in which both the collections for the projection data as well as collections to handle
  projection state and locking will be created.

### AWS Permissions

When using AWS DocumentDB, the following permissions are required at the client level:

- rds: TODO
