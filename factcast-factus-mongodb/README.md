## Usage

Allows to build projections using MongoDB/ AWS DocumentDB.

### Transactionality not supported

Currently, transactionality is not supported.

## Prerequisites

In order to use this projection provide either:

- A MongoDB client & the name of the database
- or the MongoDatabase itself.

Collections for locking and keeping track of the projection state will be created automatically, if not present.

