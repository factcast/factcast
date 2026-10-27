# Apache Wicket server UI

Approved implementation: add factcast-server-ui-wicket with all three existing UI
screens and real backend integration. Retain comparable navigation drawer, header,
forms, and results; pixel-perfect Vaadin styling is not required. Preserve login,
logout, namespace permissions, query URL state, filtering, serial/date helpers,
limit/offset, JSON export, Fact-ID/version lookup, report generation/list/refresh/
download/delete, plugin annotations/tooltips/quick filters, metrics, filesystem
and S3 report support.

Extract framework-neutral code into factcast-server-ui-common, retaining public
packages/contracts, and make both UIs and S3 storage use it. Applications select
one UI through dependencies; reject both UI implementations on the same classpath.
Use Java 21, Wicket 10.11.0, Spring Boot/Security, local assets, and serializable
page models without serialized services or streams. No database migration.

Acceptance: WicketTester and real HTTP tests cover forms, navigation, validation,
URL state, serialization, authentication, CSRF, permissions, and user-owned reports.
A seeded PostgreSQL example and browser workflow verify query, export,
transformations, and report generation/download/deletion/logout. Capture screenshots
of all three screens in Wicket and Vaadin. Run shared backend, S3, auto-configuration,
and Vaadin regression tests and repository formatting checks.
