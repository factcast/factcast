+++
draft = false
title = "Anatomy of a Fact"
description = ""

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "concept"
identifier = "anatomy"
weight = 29
+++

## Facts

FactCast is centered around *Facts*. We say Facts instead of Events, because Event has become a blurry term that could mean any number of things from a simple ```onWhatNot() ``` call handled by an Event-Loop to a ```LegalContractCreated``` with any flavor of semantics.

We decided to use the term Fact over Domain-Event because we want to highlight the notion of an Event being an immutable thing that, once it is published, became an observable Fact. 

Obviously, a Fact is history and cannot be changed, after it happened. This is one of the cornerstones of EventSourcing and provides us with Facts being **immutable**, which plays an important role when it comes to caching.

[{{<icon name="circle-arrow-right" size="small">}}Read more on caching]({{%relref "/concept/caching.md"%}})

Facts consist of two JSON documents: Header and Payload.

### The Header

consists of:

* a **required** Fact-Id 'id' of type UUID
* a **required** namespace 'ns' of type String
* an optional set of aggregateIds 'aggId' of type array of UUIDs
* an optional (but mostly used) Fact-Type 'type' of type String
* an optional Object 'meta' any number of key-value pairs, where the values are Strings
* any additional information you want to put in a Fact Header


JSON-Schema:

```jsonSchema
{
    "$schema": "http://json-schema.org/draft-04/schema#",
    "definitions": {},
    "id": "http://docs.factcast.org/example/fact.json",
    "properties": {
        "id": {
            "id": "/properties/id",
            "type": "string",
            "pattern": "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$",
        },
        "aggId": {
            "id": "/properties/aggId",
            "type": "array",
            "items":{
            	"id": "/properties/aggId/items",
            	"type": "string",
                "pattern": "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
            }
        },
        "ns": {
            "id": "/properties/ns",
            "type": "string",
        },
        "type": {
            "id": "/properties/type",
            "type": "string"
        },
        "meta": {
            "id": "/properties/meta",
            "type": "object",
            "additionalProperties": {
		"type": "string",
		"description": "Some string values"
            },
        }
    },
    "type": "object",
    "additionalProperties": {
        "type": "object"
    },
    required: ["id","ns"]
}
```

### The Metadata Object

The Meta-Data Object is optional and consist of key:value pairs. The reason for it is that implementations can filter facts on certain attributes efficiently (without indexing the whole Fact payload).
  

### The Payload

The payload has no constraints other than being a valid JSON document.

please, see GRPC docs for further details.
