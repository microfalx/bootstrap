# Instructions

This file provides guidance to any AI agents when working with code in this repository.

## Instruction precedence

When guidance conflicts, use this order:
1. Explicit user or task instructions in the current conversation.
2. This file (`AGENTS.md`).
3. The module-level `README.md`.
4. Existing conventions in the files being changed.

`CLAUDE.md` points to this file and adds no rules.

## Project overview

Bootstrap provides opinionated services and components for Spring Boot projects. It uses Java 17, Spring Boot 3.5.x,
and Apache Maven. The root `pom.xml` is a multi-module reactor with `packaging=pom`. There is no CI pipeline; verify
builds and tests manually.

Module groups (each has its own `pom.xml`):
- `base`: app, cli, configuration, core, feature, logger, metrics, registry, resource, security, store, support, test,
  trace, web
- `data`: broker, content (Apache Tika), dataset, dsv, jdbc (incl. modular DB migration), model, test
- `extension`: dos, help, mail, rest-api, rest-api-client (Retrofit), search, security, support, system,
  template (Thymeleaf), test, web
- `cloud`: google
- `ai`: api, core, lucene, provider (github, hugging-face, llama, ollama, openai), web
- `serenity`: browser testing on Serenity BDD
- `demo`: runnable Spring Boot demo app (`DemoApplication`)
- `bom`: Bill of Materials for dependency management

The root `pom.xml` depends on sibling `net.microfalx` artifacts (`lang`, `resource`, `metrics`, `tracing`, `jvm`,
`jdbcpool`, `threadpool`, and `webjar`) from separate repositories. It also uses the external parent POM
`net.microfalx:pom` and the centralized `net.microfalx:bom` for dependency management.

## Build & test

- `mvn clean install -DskipTests` — compile/install the whole reactor without tests.
- `mvn clean test` — compile and run tests.
- `mvn -pl <module> -am test` — run tests for a single module and its dependencies.
- Minimum verification before considering a change complete:
  - Docs-only change: no build required.
  - Single-module code change: `mvn -pl <module> -am test`.
  - Change touching shared/core modules or public APIs: `mvn clean test` for the whole reactor.
- Run the demo app with `mvn spring-boot:run` from the `demo` module, or run the `DemoApplication` main class.
  It serves at http://localhost:8080.
- The demo app requires a local MySQL database. See `README.md` for setup commands. It uses in-house migrations; see
  the Database access section below.

## Version control

- No enforced commit message or branch naming convention today. Write clear, descriptive commit messages
  summarizing the change and its intent.
- Run the verification level from "Build & test" above before considering a change complete.

## Code formatting

- Indentation: 4 spaces.
- Blank lines: use to separate logical blocks of code.
- Line length: maximum 120 characters.
- Use IntelliJ IDEA default code style for Java.

## Java style

Follow standard Java/Spring Boot conventions except where overridden below.

- Use UTF-8 encoding.
- Use descriptive names for classes, methods, and variables.
- Avoid `var` keyword, prefer explicit types.
- Prefer immutability. Avoid mutating shared/external state inside `for-each` loops or `Stream.forEach()`.
  Use `map`/`filter`/`collect` or build a new collection instead.
- Avoid magic numbers and strings; use constants instead.
- Check emptiness and nullness before operating on collections and strings. Use project-internal
  `net.microfalx.lang.StringUtils` and `net.microfalx.lang.ObjectUtils`, not Apache Commons or Guava.
- Comment complex or non-obvious business logic and public APIs. Keep comments clear and concise.
- Add Javadoc on public classes/methods exposed as module APIs (not required for internal/package-private code).
- Use `@Override` when overriding methods.
- Wrap 2 or more boolean conditions (e.g., in an `if`) into a named boolean variable describing the intent,
  instead of an inline compound expression.
- Prefer early returns; avoid `else` when a return can be used instead.

## Lombok annotations

- Use `@RequiredArgsConstructor` for dependency injection via constructor.
- Use `@Slf4j` for logging. The repository configures the logger field as `LOGGER`; see `lombok.config`.
- Use `@Builder(setterPrefix = "with")` for complex object creation.
- Avoid `@Data` annotation; prefer `@Getter`, `@Setter`, and `@ToString` for granular control.

## Annotations

- `@Service`: for business logic classes.
- `@Repository`: for data access classes that extend JPA repositories or interact with the database.
- `@Controller`: for web controllers.
- `@RestController`: for REST API controllers.
- `@Component`: for generic Spring components.
- `@Configuration`: for Spring configuration classes.
- Use constructor injection with `@RequiredArgsConstructor` and `final` fields. Do not use field injection except in
  tests.
- Use `@ConfigurationProperties` to bind related properties. Prefer it over multiple `@Value` annotations for 3 or more
  properties.
- `@Transactional`: only on `@Service` classes. Justify each use and ensure rollback covers the failure cases.
- Avoid circular dependencies. Do not use `@Order` to mask a circular-dependency problem. `@Order` is fine for
  legitimate bean-ordering (e.g., `@Order(Ordered.HIGHEST_PRECEDENCE)` on infrastructure services).

## Module dependencies

- Modules must not introduce circular dependencies across the reactor. A module may depend only on modules earlier in
  its group or on groups it legitimately builds on. Check the root `pom.xml` module order.
- When adding a new dependency between modules, prefer depending on an API/interface module over an
  implementation module, if one exists.

## Mappers

TODO (team decision pending): choose MapStruct or strictly static mappers project-wide. Until then, check the module
being edited for an existing mapper pattern and follow it. If there is no precedent, prefer static mappers without
extra build-time annotation processing. Mention this choice in the change review.

## Exception handling

- Avoid `throws` clauses; prefer unchecked exceptions for domain/service APIs. Keep checked exceptions only
  when required by an external API/contract.
- Each module defines one root exception extending `RuntimeException` (e.g. `AiException`, `ResourceException`,
  `CliException`). More specific exceptions extend the module's root exception, not `RuntimeException` directly
  (e.g. `AiNotFoundException extends AiException`).
- Use `@RestControllerAdvice` and `@ExceptionHandler` for global exception handling on `@RestController`s (see
  `net.microfalx.bootstrap.restapi.RestApiExceptionHandler`). Reserve plain `@ControllerAdvice` for view-based
  `@Controller` error handling, if ever needed.
- Map exceptions to appropriate HTTP status codes in REST controllers.
- Reuse/extend `net.microfalx.bootstrap.restapi.RestApiError` for error responses instead of introducing a new
  shape per module.

## Testing

Frameworks:

- JUnit 5 for unit and integration tests.
- Plain JUnit assertions for simple cases; AssertJ (`org.assertj.core.api.Assertions`) for complex assertions.
- Plain Mockito for utility and domain unit tests that do not need a Spring Boot context.
- `ServiceUnitTestCase` for service/component unit tests (see `net.microfalx.bootstrap.test.ServiceUnitTestCase`).
  It supports the Mockito extension and custom answers via classes annotated with `@AnswerFor`
  (see `net.microfalx.bootstrap.test.annotation.AnswerFor`).
- `ServiceIntegrationTestCase` for integration tests (see `net.microfalx.bootstrap.test.ServiceIntegrationTestCase`).
  It already imports core configurations and services.

Conventions:

- Use given/when/then structure in test methods.
- Do not prefix test method names with `test`. Use descriptive camelCase names indicating the behavior tested
  (e.g., `getUserByIdOk`, `getUserByIdNotFound`).
- Avoid reflection in tests when possible.
- Avoid business logic in tests; focus on behavior verification.

## Logging

- Use `@Slf4j` annotation from Lombok for logging to avoid boilerplate code with Logger instances.
- Log at appropriate levels: `DEBUG`, `INFO`, `WARN`, `ERROR`.
- Include contextual information in logs (e.g., `requestId`, `userId`, `entityId`, `operation`, `outcome`).
- Avoid logging sensitive information.
- Use structured logging for better log management.
- Format log messages with placeholders (e.g., `{}`) instead of string concatenation.

## Database access

- Prefer Spring Data JPA in this order: repository methods, JPQL `@Query`, short native `@Query`, then `QueryProvider`.
  - CRUD/simple filter -> JPA repository methods.
  - Medium-complexity portable query -> JPQL `@Query`.
  - DB-specific but short -> native `@Query`.
  - Large (upserting, multiple joins, etc.), dynamic, or vendor-specific -> `QueryProvider` + SQL resource file.
- Schema is defined in `resources/sql/TYPE/schema` and data in `resources/sql/TYPE/data`; both run automatically
  at application startup (`TYPE` = `mysql`, `postgres`, etc.; default target database is MySQL).
- Custom native queries live in `resources/sql/TYPE/queries` and run via `net.microfalx.bootstrap.jdbc.support.Query`
  (built on Spring Boot `JdbcClient`) and `net.microfalx.bootstrap.jdbc.support.QueryProvider`.
- Use transactions for operations that modify the database and ensure proper rollback on exceptions.
- Avoid N+1 query problems; use fetch joins or `@EntityGraph`.
