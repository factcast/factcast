---
title: "Tips"
type: "docs"
weigth: 2000
---

This section contains some tips and tricks that you might find useful to improve performances or to cover some corner use cases.

## @SuppressFactusWarnings

Similar to `java.lang.SuppressWarnings`, you can use this annotation to suppress warnings. You could notice these when factus encounters a class violating good practices (for instance when scanning your projection) the first time.

The annotation can be scoped to a type, method or field declaration.

It requires a value, which specifies the type of warning(s) to suppress. At the time of writing (Factcast version 0.5.2), the allowed values are:

- `SuppressFactusWarnings.Warning.ALL` suppresses all Factus related warnings
- `SuppressFactusWarnings.Warning.PUBLIC_HANDLER_METHOD` suppresses _"Handler methods should not be public"_ type of warning, caused by projection handler
  methods having a public scope
