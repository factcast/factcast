---
title: "Security"
type: docs
weight: 158
---

## Authentication & Authorization

In order to control access the FactCast Server supports a very basic way of defining which client is allowed to do what.
The security feature is enabled by default, but can be disabled (for integration tests for example) by
setting `factcast.security.enabled` to false.

In order to make use of the security features, a Bean of type `FactCastAccessConfig` must be defined. This is done
either by providing one
in your FactCast Server's context, or by using the dead-simple approach to put a `factcast-access.json` on the root of
your classpath or at `/config/` to deserialize it from there.

Example below.

Now, that you've defined the access configuration, you also need to define the secrets for each account. Again, you can
do that programmatically by providing a FactCastSecretsProperties, or by defining a property for each account like this:

```
factcast.access.secrets.brain=world
factcast.access.secrets.pinky=narf
factcast.access.secrets.snowball=grim
```

The catch with this simple approach of course is, _that credentials are stored in plaintext_ in the server's classpath,
but remember it is just a dead-simple approach to get you started. Nobody says, that you cannot provide this information
with a layer of your docker container, pull it from the AWS Parameter Store etc...

If FactCast misses a secret for a configured account on startup, it will stop immediately. On the other hand, if there
is a secret defined for a non-existing account, this is just logged (WARNING-Level).

The contents of factcast-access.json might look like:

```json
{
	"accounts": [
		{
			"id": "brain",
			"roles": ["anything"]
		},
		{
			"id": "pinky",
			"roles": ["anything", "limited"]
		},
		{
			"id": "snowball",
			"roles": ["readOnlyWithoutAudit"]
		}
	],
	"roles": [
		{
			"id": "anything",
			"read": {
				"include": ["*"]
			},
			"write": {
				"include": ["*"]
			}
		},
		{
			"id": "limited",
			"read": {
				"include": ["*"],
				"exclude": ["secret"]
			},
			"write": {
				"exclude": ["audit*"]
			}
		},
		{
			"id": "readOnlyWithoutAudit",
			"read": {
				"include": ["*"],
				"exclude": ["audit*", "secret"]
			},
			"write": {
				"exclude": ["*"]
			}
		}
	]
}
```

Where `pinky` & `brain` are authorized to use the full FactStore's functionality (with 'pinky' not being able to write
to namespaces that start with 'audit') whereas `snowball` can only read everything but 'audit'-namespaces, but not write
anything.

In case of conflicting information:

- explicit wins over implicit
- exclude wins over include

Note, there is no fancy wildcard handling other than a trailing '\*'.

see
module [examples/factcast-example-server-basicauth](https://github.com/factcast/factcast/tree/master/factcast-examples/factcast-example-server-basicauth)
for an example

### Using BasicAuth from a client

From a client's perspective, all you need to do is to provide credentials. Once the credentials are configured, they are
used on every request in a Basic-Auth fashion (added header to request).

`factcast.grpc.client.user` and `factcast.grpc.client.password` are the properties to set.

You can always use environment variables or a `-D` switch in order to inject the credentials.

see
module [examples/factcast-example-client-basicauth](https://github.com/factcast/factcast/tree/master/factcast-examples/factcast-example-client-basicauth)
for an example

### Customizing Credential Loading

If you dont want to configure your passwords via properties, you can provide either a custom `FactCastSecretProperties`
bean or an implementation of a `UserDetailsService`.
That's a simple interface coming from Spring Security which provides a mapping method from `username` to user. In our
case we have to return a `FactCastUser`.

If you want to externalize secret loading but want to keep the `factcast-access.json` file for managing authorization an
implementation of such a `UserDetailsService` could look like this:

```
@Bean
UserDetailsService userDetailsService(FactCastAccessConfiguration cc, PasswordEncoder passwordEncoder) {
    return username -> {
        // fetching account info from fact-access.json
        Optional<FactCastAccount> account = cc.findAccountById(username);

        // your way to fetch the user + password
        User user = loadUserByName(username);

        return account
            .map(acc -> new FactCastUser(acc, passwordEncoder.encode(user.getPassword())))
            .orElseThrow(() -> new UsernameNotFoundException(username));
    };
}
```
