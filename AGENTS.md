# Instructions

This file provides guidance to any AI agents when working with code in this repository.

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
`net.microfalx:pom`. A centralized BOB is used for dependency management, provided by `net.microfalx:bom` dependency in the root `pom.xml`.

## Build & test

- `mvn clean install -DskipTests` — compile/install the whole reactor without tests.
- `mvn clean test` — compile and run tests.
- `mvn -pl <module> -am test` — run tests for a single module and its dependencies.
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
    - Avoid mutations of objects, specially when using for-each loops or Stream API using `forEach()`.
    - Avoid magic numbers and strings; use constants instead.
    - Check emptiness and nullness before operations on collections and strings using `net.microfalx.lang.StringUtils` and `net.microfalx.lang.ObjectUtils` (project-internal utilities, not Apache Commons or Guava).
    - Avoid methods using `throws` clause; prefer unchecked exceptions.
- Avoid comments, unless the business logic is complex and not self-explanatory. If comments are necessary, ensure they are clear and concise.
- Comments could be applied for: cron expressions, regex patterns, TODOs, or given/when/then separation in tests.
- Use `@Override` annotation when overriding methods.
- Avoid `Objects.isNull()` and `Objects.nonNull()` for one or two variables; prefer direct null checks for better performance.
- Wrap multiple conditions in a boolean variable for better readability.
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
- `@Autowired`: prefer constructor injection for production code, or when the number of injected fields is small; field injection only for tests.
- `@ConfigurationProperties`: for binding related properties, avoid multiple `@Value` annotations. From more than 2 properties, consider using this annotation.
- `@Transactional`: only `@Service` classes should be annotated with `@Transactional`, when needed.
- Circular dependencies should be avoided. Avoid `@Order` annotation for dependency resolution.

## Mappers

Not yet decided by the team — choose MapStruct or strictly static mappers. Don't assume either convention.

## Exception handling

- Custom exceptions: create custom domain exception classes extending `RuntimeException`.
- Global exception handler: use `@ControllerAdvice` and `@ExceptionHandler` to handle exceptions globally.
- HTTP status codes: map exceptions to appropriate HTTP status codes in REST controllers.
- Error response structure: define a consistent error response structure.

## Testing

- Use JUnit 5 for unit and integration testing.
- Use Mockito for mocking dependencies in unit tests.
- Do not add a `test` prefix to test method names; instead, use descriptive names that indicate the behavior being tested.
- Use `ServiceUnitTestCase` for unit tests (see `net.microfalx.bootstrap.test.ServiceUnitTestCase`); it is supported by Mockito extension and annotation with custom answers provided by classes annotated with @AnswerFor (see `net.microfalx.bootstrap.test.annotation.AnswerFor`).
- Use `ServiceIntegrationTestCase` for integration tests (see `net.microfalx.bootstrap.test.ServiceIntegrationTestCase`) and it already imports core configurations & services.
- Use given/when/then structure in test methods for clarity.
- Method naming should follow camelCase convention for test methods (e.g., `getUserByIdOk`, `getUserByIdNotFound`).
- Avoid reflection in tests, when possible.
- Avoid business logic in tests; focus on behavior verification.

## Logging

- Use `@Slf4j` annotation from Lombok for logging to avoid boilerplate code with Logger instances.
- Log at appropriate levels: `DEBUG`, `INFO`, `WARN`, `ERROR`.
- Include contextual information in logs (e.g., request IDs, user IDs).
- Avoid logging sensitive information.
- Use structured logging for better log management.
- Format log messages with placeholders (e.g., `{}`) instead of string concatenation.

## Database access

- Use Spring Data JPA for database access as much as possible.
- For complex queries, use `@Query` annotation with JPQL, or native SQL for simple queries, in repository interfaces.
- Schema is defined in `resources/sql/TYPE/schema` and data in `resources/sql/TYPE/data`, and are automatically executed at application startup (`TYPE` = `mysql` for MySQL, `TYPE` = `postgres` for Postgres and so on; default target database is mysql).
- Custom (native) queries are stored in `resources/sql/TYPE/queries` and can be executed using `net.microfalx.bootstrap.jdbc.support.Query` (built on top of Spring Boot `JdbcClient`) and `net.microfalx.bootstrap.jdbc.support.QueryProvider` to create the database-specific queries.
- For complex queries (upserting, multiple joins, etc.) or in the absence of entities and repositories, always consider using custom native queries stored in `resources/sql/TYPE/queries`.
- Use transactions for operations that modify the database, and ensure that they are properly rolled back in case of exceptions.
- Avoid N+1 query problems by using `fetch` joins or `@EntityGraph`.
