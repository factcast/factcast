### factcast-client-cache

Adds local caching to FactCast-clients by using Springs @Cacheable facility. 
When using a CachingFactCast-Instance, all subscriptions for Facts will be transformed into Id subscriptions and the facts are fetched and cached.

Note, that this is not easily faster than streaming events directly from factcast, but might be helpful in bandwidth restricted environments or where one Fact is likely to be needed by **many** subscriptions within one process.  