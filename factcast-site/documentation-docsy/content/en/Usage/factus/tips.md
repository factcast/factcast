---
title: "Tips"
type: "docs"
weigth: 2000
---

This section contains some tips and tricks that you might find useful to improve performances or to cover some corner use cases.

## @SuppressFactusWarnings

Similar to `java.lang.SuppressWarnings`, you can use this annotation to suppress warnings. You could notice these when your application using Factus is starting
up.

The annotation can be scoped to a type (Class, interface, or enum), a method or a field declaration.

It requires a value, which specifies the type of warning(s) to suppress. At the time of writing (Factcast version 0.5.1), the allowed values are:

- `SuppressFactusWarnings.Warning.ALL` suppresses all Factus related warnings
- `SuppressFactusWarnings.Warning.PUBLIC_HANDLER_METHOD` suppresses *"Handler methods should not be public"* type of warning, caused by projection handler
  methods having public scope
