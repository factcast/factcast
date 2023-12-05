---
title: "Deployment Options"
type: docs
weight: 900
description: Options for deploying the FactCast server-ui.
---

The FactCast server-ui can be deployed together with the server or standalone in a separate container. It requires the 
same configuration parameters that are used for the server itself (documented [here](../../Setup/properties.md)). 
In addition, you should set `readOnlyModeEnabled` to true.

## Environment Variables

| Variable                              | Description            | Type                |
|---------------------------------------|:-----------------------|:--------------------|
| factcast.store.readOnlyModeEnabled    | Should be set to true. | Boolean             |
