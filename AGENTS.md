# AGENTS.md

## Technology used

- Java 21 where possible, Java 11 in modules that are configured accordingly
- Maven
- Spring Boot3
- JUnit5
- Mockito
- lombok

## Coding Conventions

- Prefer `final var` over explicit types for local variables (Java 21)
- Use Lombok extensively (`@Value`, `@Getter`, `@Builder`, `@NonNull`)
- Do not create unit tests for @NonNull constraints
- Use Google Java Format for code style
