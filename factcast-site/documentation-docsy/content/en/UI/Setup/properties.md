---
title: "Properties"
type: docs
weight: 200
description: Properties you want to use to configure FactCast UI.
---

## UI

This setting should be set in every instance that factcast-server-ui is part of.

| Property                 | Description                                                                                                                                 | Default             |
| ------------------------ | :------------------------------------------------------------------------------------------------------------------------------------------ | :------------------ |
| vaadin.productionMode    | Should be set to true, otherwise vaadin tries to generate a dev bundle which is not necessary, and probably will fail.                      | false               |
| factcast.ui.reports.path | The path under which reports are stored if no external ReportStore is configured. Set to a location within `/tmp` for readonly filesystems. | factcast-ui/reports |
