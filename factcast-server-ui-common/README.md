# FactCast Server UI common backend

Framework-neutral query models, FactStore repository adapters, subscription observers,
report storage contracts and filesystem storage, JSON plugin processing, and UI metrics.
Used by both the Vaadin and Apache Wicket server UIs and by the S3 report store.

Public classes retain their existing `org.factcast.server.ui` package names. UI-specific
authentication adapters, page components, and download endpoints belong to their
respective UI modules. Filesystem download URLs use the current servlet context and
the existing `/files/` endpoint.
