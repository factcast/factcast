---
title: "FactStream Design"
weight: 30
type: docs
---

## Fact Stream design

If you are familiar with other products that store Facts, you might be familiar with the concept of an Fact-Stream. In most solutions, the consumer subscribes to particular stream of Facts and then picks those of interest for him.

### FactStream per Aggregate

A simple example would be to have all Facts regarding a particular Aggregate Root in one FactStream, identified by the Aggregate's id. Something along these lines:

```
User-1234
	UserCreated
	UserNameChanged
	UserPasswordResetRequested
	UserPasswordReset
```

While this kind of Stream design makes it trivial to find all the Facts that have to be aggregated to reconstruct the state of User(id=1234), you are not done, yet.

### Facts that are not picked by Aggregate-Id

Let's just say, we want to create a report on how many _UserPasswordReset_ Facts there are per Month. Here we are facing the problem, that (well after publishing the Fact), we need to pick only those _UserPasswordReset_ from the Store, regardless of any Aggregate relation.

Here we see, that relying only on ahead of time tagging and categorizing of Facts would break our necks. If we don't have a way to express out interest in particular Facts, maybe based on criteria, we come up with a year after publishing the Facts, FactSourcing either looses its point or at least get frustratingly inefficient, because you'd need to iterate any Fact there is, and filter on the consumer side.

Here we are faced with the necessity of Store-Side filtering of FactStreams.

### Facts that have an effect on multiple Aggregates

What about Facts, that have impact to more than one Aggregates?

```
User-1234
	UserCreated
	UserFollowedUser
```

Here the second Fact obviously has impact on both the following, as well as the user being followed.

#### Bad Practice: Splitting Facts

While we have seen people split the semantically unique Fact _UserFollowedUser_ into _UserFollowedUser_ and _UserWasFollowed_, **we don't believe in that approach**, as it creates lots of Facts that are only loosely connected to the original fact, that some user clicked "follow" on some other users profile.

More formally: by doing that, the publisher has to know about the domain model in order to correctly slice and direct Facts to the Aggregates. Not at all a good solution, as Facts last forever, while Domain Models **do** change over time, if you want them to, or not.

#### Include Facts in different Streams

Certainly better is the idea of publishing one _UserFollowedUser_, and make both Aggregates (The user following and the one being followed) consume this Fact. For that reason, some solutions (like FactStore, for instance) give you the opportunity to pick that Fact and place it into both Fact Streams, the originators and the targeted users.
In order to do that, you inject JavaScript Projection code into the FactStore, that builds a particular FactStream directed to the consumer.

## Conclusion

As a consumer, you need to be able to express, what Facts are of interest to you. While tagging/categorizing those Facts might help when filtering, you cannot possibly rely on being able to predict all future needs when publishing an Fact.

The actual filtering of FactStreams according to the consumer's needs should also probably be done within the Store, for efficiency reasons. You don't want to flood the network with Facts that end up being filtered out, if at all possible.

This requires a **flexible way of expressing**, what Facts you are interested in, ultimately leading to **scripted Predicates**.

Filtering could be done either ahead of time (by emitting Facts into different Streams and creating new Streams whenever a consumer's need changes), or just-in-time, which might have some impact on performance.

[Fact Specification]({{%relref "/concept/factspec.md"%}})
