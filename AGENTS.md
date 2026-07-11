# Instructions

This file provides guidance to any AI agents when working with code in this repository.

## Instruction precedence

When guidance conflicts, resolve in this order: explicit user/task instructions in the current conversation >
this file (`AGENTS.md`) > module-level `README.md` > existing conventions in the file(s) being touched. `CLAUDE.md`
is a pointer to this file and carries no additional rules.

## Project overview

Bootstrap provides building blocks for Spring Boot projects — opinionated custom services/components on top of
Spring Boot. Java 17, Spring Boot 3.5.x, built with Apache Maven as a multi-module reactor (root `pom.xml` is
`packaging=pom`). No CI pipeline exists; build/test verification is manual.

Module groups (each has its own `pom.xml`):
- `base`: app, cli, configuration, core, feature, logger, metrics, registry, resource, security, store, support, test, trace, web
- `data`: broker, content (Apache Tika), dataset, dsv, jdbc (incl. modular DB migration), model, test
- `extension`: dos, help, mail, rest-api, rest-api-client (Retrofit), search, security, support, system, template (Thymeleaf), test, web
- `cloud`: google
- `ai`: api, core, lucene, provider (github, hugging-face, llama, ollama, openai), web
- `serenity`: browser testing on Serenity BDD
- `demo`: runnable Spring Boot demo app (`DemoApplication`)
- `bom`: Bill of Materials for dependency management

The root `pom.xml` depends on several sibling `net.microfalx` artifacts (`lang`, `resource`, `metrics`,
`tracing`, `jvm`, `jdbcpool`, `threadpool`, `webjar`) from separate repos, plus an external parent POM
`net.microfalx:pom`. A centralized BOM is used for dependency management, provided by `net.microfalx:bom` dependency in the root `pom.xml`.

## Build & test

- `mvn clean install -DskipTests` — compile/install the whole reactor without tests.
- `mvn clean test` — compile and run tests.
- `mvn -pl <module> -am test` — run tests for a single module and its dependencies.
- Minimum verification before considering a change complete:
  - Docs-only change: no build required.
  - Single-module code change: `mvn -pl <module> -am test`.
  - Change touching shared/core modules or public APIs: `mvn clean test` for the whole reactor.
- Run the demo app: `mvn spring-boot:run` from the `demo` module, or run the `DemoApplication` main class. Serves at http://localhost:8080.
- The demo app requires a local MySQL database (see `README.md` for the exact `CREATE USER`/`CREATE DATABASE` SQL) and uses in-house migrations (see Database access section below).

## Code formatting

- Indentation: 4 spaces.
- Blank lines: use to separate logical blocks of code.
- Line length: maximum 120 characters.
- Use IntelliJ IDEA default code style for Java.

## Java style

Standard Java/Spring Boot guidelines apply unless overridden below.

- Use UTF-8 encoding.
- Use descriptive names for classes, methods, and variables.
- Avoid `var` keyword, prefer explicit types.
- Preference for immutability:
    - Avoid mutating shared/external state inside `for-each` loops or `Stream.forEach()`; prefer `map`/`filter`/`collect`
      or building a new collection instead of accumulating into a pre-existing mutable variable.
    - Avoid magic numbers and strings; use constants instead.
    - Check emptiness and nullness before operations on collections and strings using `net.microfalx.lang.StringUtils` and `net.microfalx.lang.ObjectUtils` (project-internal utilities, not Apache Commons or Guava).
    - Avoid methods using `throws` clause; prefer unchecked exceptions for domain/service APIs; keep checked exceptions when required by external API/contracts.
- Comments are required on complex or non-obvious business logic, APIs (contracts). Ensure they are clear and concise.
- Use `@Override` annotation when overriding methods.
- Wrap 2 or more boolean conditions (e.g., in an `if`) into a named boolean variable describing the intent,
  instead of an inline compound expression.
- Prefer early returns.
- Avoid `else` statements when not necessary and try early returns.

## Lombok annotations

- Use `@RequiredArgsConstructor` for dependency injection via constructor.
- Use `@Slf4j` for logging (logger field name is `LOGGER`, configured in `lombok.config`; accessors chain via `lombok.accessors.chain=true`).
- Use `@Builder(setterPrefix = "with")` for complex object creation.
- Avoid `@Data` annotation; prefer `@Getter` and `@Setter` for granular control.

## Annotations

- `@Service`: for business logic classes.
- `@Repository`: for data access classes that extend JPA repositories or interact with the database.
- `@Controller`: for web controllers.
- `@RestController`: for REST API controllers.
- `@Component`: for generic Spring components.
- `@Configuration`: for Spring configuration classes.
- `@Autowired`: constructor injection only, use `@RequiredArgsConstructor` with `final` fields, no field injection except tests.
- `@ConfigurationProperties`: for binding related properties, avoid multiple `@Value` annotations. From more than 2 properties, consider using this annotation.
- `@Transactional`: preferably only `@Service` classes should be annotated with `@Transactional`, when needed and exceptions must be justified and properly handled.
- Circular dependencies should be avoided. Avoid `@Order` annotation for dependency resolution.

## Mappers

Not yet decided by the team — choose MapStruct or strictly static mappers. Don't assume either convention;
check the module being edited for an existing mapper pattern and follow it. If the module has no precedent,
prefer strictly static mappers (no extra build-time annotation processing) and flag the choice for review.

## Exception handling

- Custom exceptions: create custom domain exception classes extending `RuntimeException`.
- Global exception handler: use `@ControllerAdvice` and `@ExceptionHandler` to handle exceptions globally.
- HTTP status codes: map exceptions to appropriate HTTP status codes in REST controllers.
- Error response structure: define a consistent error response structure.

## Testing

- Use JUnit 5 for unit and integration testing.
- Use plain JUnit assertions for simple cases and prefer AssertJ for more complex assertions (see `org.assertj.core.api.Assertions`).
- Do not add a `test` prefix to test method names; instead, use descriptive names that indicate the behavior being tested.
- Use plain Mockito for simple unit test cases (utils/domain classes), where Spring Boot context is not required.
- Use `ServiceUnitTestCase` for complex (services/components/etc) unit tests (see `net.microfalx.bootstrap.test.ServiceUnitTestCase`); it is supported by Mockito extension and annotation with custom answers provided by classes annotated with @AnswerFor (see `net.microfalx.bootstrap.test.annotation.AnswerFor`).
- Use `ServiceIntegrationTestCase` for integration tests (see `net.microfalx.bootstrap.test.ServiceIntegrationTestCase`) and it already imports core configurations & services.
- Use given/when/then structure in test methods for clarity.
- Method naming should follow camelCase convention for test methods (e.g., `getUserByIdOk`, `getUserByIdNotFound`).
- Avoid reflection in tests, when possible.
- Avoid business logic in tests; focus on behavior verification.

## Logging

- Use `@Slf4j` annotation from Lombok for logging to avoid boilerplate code with Logger instances.
- Log at appropriate levels: `DEBUG`, `INFO`, `WARN`, `ERROR`.
- Include contextual information in logs (e.g., `requestId`, `userId`, `entityId`, `operation`, `outcome`).
- Avoid logging sensitive information.
- Use structured logging for better log management.
- Format log messages with placeholders (e.g., `{}`) instead of string concatenation.

## Database access

- Use Spring Data JPA for database access as much as possible (JPA -> JPQL -> small JPA native queries ->  `QueryProvider`).
  - CRUD/simple filter -> JPA repository methods
  - Medium complexity portable query -> JPQL @Query
  - DB-specific but short -> native @Query
  - Large (upserting, multiple joins, etc.)/dynamic/vendor-specific/ -> QueryProvider + SQL resource file
- Schema is defined in `resources/sql/TYPE/schema` and data in `resources/sql/TYPE/data`, and are automatically executed at application startup (`TYPE` = `mysql` for MySQL, `TYPE` = `postgres` for Postgres and so on; default target database is mysql).
- Custom (native) queries are stored in `resources/sql/TYPE/queries` and can be executed using `net.microfalx.bootstrap.jdbc.support.Query` (built on top of Spring Boot `JdbcClient`) and `net.microfalx.bootstrap.jdbc.support.QueryProvider` to create the database-specific queries.
- Use transactions for operations that modify the database, and ensure that they are properly rolled back in case of exceptions.
- Avoid N+1 query problems by using `fetch` joins or `@EntityGraph`.
