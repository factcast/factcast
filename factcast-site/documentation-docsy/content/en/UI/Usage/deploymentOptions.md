---
title: "Deployment Options"
type: docs
weight: 900
description: Options for deploying the FactCast server-ui.
---

## Deployment Options

The FactCast server-ui can be deployed in one of three ways:

1. as part of the factcast server with gRPC layer.
2. standalone instance without gRPC layer using a jdbc connection.
3. standalone instance with gRPC client facilitating factcast-server over gRPC.

### Security

Settings are documented [here](../Setup/security.md).
If security is disabled you can log in with any username and `security_disabled` as password.

### Properties

Properties depend on the deployment option.

Option 1)

# TODO: finish

In case of deployment option 2) the instance requires the same configuration parameters that are used for the server
itself (documented [here](../../Setup/properties.md)). Also setting `factcast.store.readOnlyModeEnabled=true` is
recommended.

Option 3) # TODO: finish
