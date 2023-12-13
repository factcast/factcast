---
title: "Deployment Options"
type: docs
weight: 900
description: Options for deploying the FactCast server-ui.
---

## Deployment Options

The FactCast Server-UI can be deployed in different ways:

### Option 1: as part of the factcast server

Just add the dependency factcast-server-ui to your server project (next to server-grpc), so that you can use the UI
directly from within the Server. In
this configuration, the UI uses the (process-local) FactStore interface to talk to your store, and (almost) no
additional configuration is needed.

(!) Beware that this way, the UI is vulnerable to anyone, who can access the Server via HTTP, obviously.

### Option 2: as a standalone instance accessing the Database

You can add a new project to your landscape that just acts as a UI Server. In this configuration you don't even need
the gRPC layer (factcast-server-grpc) because it uses a jdbc connection to your database directly. The config is
basically the
same than with your FactCast Server (in terms of how to access the backing database), but additionally, you may want
to set the role of this instance to readOnly by setting `factcast.store.readOnlyModeEnabled=true`.

### Option 3: as standalone instance facilitating an existing factcast-server over gRPC.

This is useful when you don't want to publish the UI (much recommended) and also cannot / don't want to access the
Database directly. This
way, the server-ui is basically just another client talking to the FactCast Server, and the configuration is done
accordingly.

### Security

The UI uses spring-security. Some helpful settings are documented [here](../Setup/security).
If security is disabled you can log in with any username and `security_disabled` as password.
