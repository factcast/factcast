+++
draft = false
title = "Boot gRPC BasicAuth"
description = ""
date = "2019-05-17T09:42:24+02:00"
weight = 170

creatordisplayname = "Uwe Schäfer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "setup"
identifier = "grpc-config-basicauth"

+++

## Access control via Basic Auth

In order control access to a GRPC FactStore and to distinguish the two different basic client roles (ReadOnly and FullAccess), FactCast support usage of secrets exchange via BasicAuth. Note that this is just a very basic way of providing authentication that - if used in production - should be accompanied by using TLS in order not to expose secrets over the wire.

Also the current implementation - while functional - is just a starting point. FactCast uses standard Spring Security, so that you have plenty of options to tailor security to your needs.

#### Using BasicAuth from a client

From a client's perspective, all you need to do is to provide credentials. Once the credentials are configured, they are used on every request in a Basic-Auth fashion (added header to request).

In order to define credentials, just set the appropriate property to a value of the format 'username:password', just as you would type them into your browser when a basic-auth popup appears.

```
# if this property is set with a value of the format 'username:password', basicauth will be used.
grpc.client.factstore.credentials=myUserName:mySecretPassword
```

You can always use environment variables or a `-D` switch in order to inject the credentials.

see module [examples/factcast-example-client-basicauth](https://github.com/Mercateo/factcast/tree/master/factcast-examples/factcast-example-client-basicauth) for an example

 
### Using BasicAuth on the Server

On the server, in order to provide downward compatibility, security is disabled by default (this will change in the future, mongoDB taught us well). Once security is enabled, non-authenticated users will not be allowed to work with the grpc factstore anymore.

There are three roles a client can take:

* not authenticated (will be rejected)
* authenticated and authorized to read only access
* authenticated and authorized for full access (same as with security disabled)

The distinction of fullAccess vs readOnly comes in handy for instance in System Integration, where a downstream subsystem consumes published facts from upstream, but is not allowed to write into the upstream's FactCast by any means.
 
In order to enable security, a Bean of type `CredentialConfiguration` must be defined. This is done either by providing one in your FactCast Server's context, or by using the dead-simple approach to put a `factcast-security.json` on the root of your classpath to deserialize it from there. The catch with this simple approach of course is, *that credentials are stored in plain* in the server's classpath, but remember it is just a dead-simple approach to get you started.

The contents of this file might look like:

```
{
	"fullAccess": [
		{
			"name": "pinky",
			"password": "narf"
		},
		{
			"name": "brain",
			"password": "zort"
		},
	],
	"readOnlyAccess": [
	 	{
			"name": "snowball",
			"password": "BillGrates"
		}
	]
}

```

Where `pinky` & `brain` are authorized to use the full FactStore's functionality whereas `snowball` can only read, but not change anything.

see module [examples/factcast-example-server-basicauth](https://github.com/Mercateo/factcast/tree/master/factcast-examples/factcast-example-server-basicauth) for an example
