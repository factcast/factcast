## Usage

Allows to build lightweight projections for AWS DynamoDB. Requires one table for persisting the projection state & lock in addition to the tables for the projection's data.

### Transactionality not supported 
Since DynamoDb does not support transactions that consist of both write and read operations no transactionality is supported.
This means that this projection is not suited for use-cases that require changes to multiple entires when handling a single event,
neither for ones that have a high throughput.

In addition, fact processing is not atomically and any failure while handling a fact will leave the projection in a broken state 
that can only be fixed by restarting it. 


## Prerequisites

In order to use ths projection provide the following:
- DynamoDb client
- Name of the DynamoTable to handle the state & lock (one per service).
- Required AWS Permissions

### AWS Permissions

The following permissions are required at the client level:
- dynamodb:DeleteItem
- dynamodb:GetItem
- dynamodb:PutItem
- dynamodb:Scan
- dynamodb:UpdateItem
