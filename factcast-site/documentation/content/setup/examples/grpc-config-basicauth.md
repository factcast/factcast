+++
draft = false
title = "Boot gRPC BasicAuth"
description = ""
date = "2019-05-17T09:42:24+02:00"
weight = 170

creatordisplayname = "Uwe Schäfer"
creatoremail = "uwe@codesmell.de"

[menu.main]
parent = "setup"
identifier = "grpc-config-basicauth"

+++

## Access control via Basic Auth

In order control access to a GRPC FactStore and to distinguish the two different basic client roles (ReadOnly and FullAccess), FactCast support usage of secrets exchange via BasicAuth. Note that this is just a very basic way of providing authentication that - if used in production - should be accompanied by using TLS in order not to expose secrets over the wire.

Also the current implementation - while functional - is just a starting point. FactCast uses standard Spring Security, so that you have plenty of options to tailor security to your needs.

### Using BasicAuth from a client

From a client's perspective, all you need to do is to provide credentials. Once the credentials are configured, they are used on every request in a Basic-Auth fashion (added header to request).

In order to define credentials, just set the appropriate property to a value of the format 'username:password', just as you would type them into your browser when a basic-auth popup appears.

```
# if this property is set with a value of the format 'username:password', basicauth will be used.
grpc.client.factstore.credentials=myUserName:mySecretPassword
```

You can always use environment variables or a `-D` switch in order to inject the credentials.

see module [examples/factcast-example-client-basicauth](https://github.com/factcast/factcast/tree/master/factcast-examples/factcast-example-client-basicauth) for an example

 
### Using BasicAuth on the Server

On the server, in order to provide downward compatibility, security is disabled by default (this will change in the future, mongoDB taught us well). Once security is enabled, non-authenticated users will not be allowed to work with the grpc factstore anymore.

In order to enable security, a Bean of type `FactCastAccessConfig` must be defined. This is done either by providing one in your FactCast Server's context, or by using the dead-simple approach to put a `factcast-access.json` on the root of your classpath to deserialize it from there. 

Example below.

Now, that you've defined the access configuration, you also need to define the secrets for each account. Again, you can do that programmatically by providing a FactCastSecretsProperties, or by defining a property for each account like this:

```
factcast.access.secrets.brain=world
factcast.access.secrets.pinky=narf
factcast.access.secrets.snowball=grim
```

The catch with this simple approach of course is, *that credentials are stored in plaintext* in the server's classpath, but remember it is just a dead-simple approach to get you started. Nobody says, that you cannot provide this information with a layer of your docker container, pull it from the AWS Parameter Store etc...

If FactCast misses a secret for a configured account on startup, it will stop immediately. On the other hand, if there is a secret defined for a non-existing account, this is just logged (WARNING-Level).



The contents of factcast-access.json might look like:

```
{
	"accounts": [
		{
			"id": "brain",
			"roles": [
				"anything"
			]
		},
		{
			"id": "pinky",
			"roles": [
				"anything","limited"
			]
		},
		{
			"id": "snowball",
			"roles": [
				"readOnlyWithoutAudit"
			]
		}
	],
	"roles": [
		{
			"id": "anything",
			"read": {
				"include":["*"]
			},
			"write": {
				"include":["*"]
			}
		},
		{
			"id": "limited",
			"read": {
				"include":["*"],
				"exclude":["secret"]
			},
			"write": {
				"exclude":["audit*"]
			}
		},
		{
			"id": "readOnlyWithoutAudit",
			"read": {
				"include":["*"],
				"exclude":["audit*","secret"]
			},
			"write": {
				"exclude":["*"]
			}
		}		
	]
}

```

Where `pinky` & `brain` are authorized to use the full FactStore's functionality (with 'pinky' not being able to write to namespaces that start with 'audit') whereas `snowball` can only read everything but 'audit'-namespaces, but not write anything.

In case of conflicting information:

* explicit wins over implicit
* exclude wins over include

Note, there is no fancy wildcard handling other than a trailing '*'.

see module [examples/factcast-example-server-basicauth](https://github.com/factcast/factcast/tree/master/factcast-examples/factcast-example-server-basicauth) for an example
