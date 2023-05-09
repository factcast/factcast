---

title: "Fact Specification"
weight: 20
type: docs
----------

When consumers subscribe to a Stream of Events, it has to express, which Events he wants to receive. The more precise this can be done, the less resources like bandwidth, CPU-Time etc. are wasted.

As discussed [here](../factstreams), there are more than one ways to do it. We decided to go for just-in-time filtering in FactCast. Here is why:

#### The problem with FactStream-IDs

[EventStore](https://geteventstore.com) – which is a wonderful product by the way - is a good example of _Filtering/Transforming/Meshing_ of Events ahead of time. You inject JavaScript Code as a so-called 'Projection' into the Server, that builds a new Stream of Events by picking, and maybe even transforming Events into a new Stream with a particular ID. A consumer then subscribes to that particular EventsStream, thus gets exactly what he expects.

While that is a wonderful concept, that guarantees very good performance when actually streaming, it highlights a little organizational difficulty: Where to put this code? This is a problem similar to automatically maintaining Schemas in a relational Database, tackled by Liquibase et al.

- How can an application know, if a matching Projection already exists and is what is expected?
- How to coordinate the creation of relevant Projections in the face of horizontal scalability (many instances of you application coming to live at roughly the same point in time) and
- what about carnary releases with changing (probably not changing, but replacing) Projections?

While these Problems are certainly solvable, we went for an easier, but certainly not as fast solution:

### Just In Time Filtering

According to a list of Fact-Specifications, the matching Facts are queried from the database just in time. When querying lots of Facts (like for instance following from scratch), we use a GIN Index to find the matching facts in the Database easily. When reaching the end of the Stream, the index is no longer used, as scanning the 'few' new rows that were inserted in a limited time-frame is easier than iterating the index.

In order to match with a high selectivity, this query uses some (in parts optional, but defined) Attributes from the JSON-Header of a Fact. In many cases, this filtering is already sufficient to reduce the number of misses (Facts that are sent to the consumer, but discarded there) to either zero or something very near.

### Specification

In order to efficiently select matching Facts from the Database, a consumer should provide precise information about which Facts match, and which do not.

In order to do that, a list of FactSpec-Objects is transferred on subscription. Any Fact that matches **ANY ONE** of the specifications, will be sent to the consumer.

FactSpec-Objects must define a **ns** attribute. The rest is actually optional:

| Attribute      | Type                               | Semantics                                        |
|:---------------|:-----------------------------------|:-------------------------------------------------|
| ns             | String                             | Namespace                                        |
| type           | String                             | Type of Fact                                     |
| aggId          | UUID                               | Aggregate-ID                                     |
| meta           | JSON Object with String Properties | A list of String key-value pairs (Tags) to match |
| jsFilterScript | String (JavaScript)                | Scripted Predicate, see below                    |

Of course, **all** of the requirements defined in a FactSpec have to be met for a Fact to be matched.

### Post-Query Filtering / Scripted Predicates

As discussed [here](../factstreams), there are situations, where these tagging/categorizing means are not enough, because you just do not have this information in the header, or you need some more fine grained control like Range-expressions etc.

This is where scripted Predicates come in.

Additionally to the above Attribute Specification a matching Fact must adhere to, you can define a JavaScript Predicate, that decides, if the Fact should be matching or not. In order to use this feature, the consumer provides a script as part of the specification, that implements a predicate function being passed the header and the payload of the Event in question, and is expected to return true if it should be sent to the consumer.

Example:

```javascript
function (header,payload) {
 return
 	header.myExtraAttribute.userLevel > 5
 	&&
 	payload.userBeingFollowed.countryCode = 'DE';
}
```

Please be aware, that using this feature increases load on the FactCast Server considerably. Also make sure, that the rest of the FactSpec is detailed enough to prefilter non-matching Facts at the database-level.

To say it again: **ONLY MATCH IN A SCRIPT, WHAT CANNOT BE FILTERED ON OTHERWISE**
