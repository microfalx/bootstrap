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
- `base`: app, cli, configuration, core, diagnostics, feature, logger, metrics, registry, resource, security, store,
  support, test, trace, web
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
- `mvn -pl <module> -Dtest=ClassName#methodName test` — run a single test method.
- Minimum verification before considering a change complete:
  - Docs-only change: no build required.
  - Single-module code change: `mvn -pl <module> -am test`.
  - Change touching shared/core modules or public APIs: `mvn clean test` for the whole reactor.
- Run the demo app with `mvn spring-boot:run` from the `demo` module, or run the `DemoApplication` main class.
  It serves at http://localhost:8080.
- The demo app requires a local MySQL database. See `README.md` for setup commands. It uses in-house migrations; see
  the Database access section below.

## Version control

- No enforced commit message. Write clear, descriptive commit messages
  summarizing the change and its intent. See "Build & test" for the required verification level before committing.
- Branching follows the MAJOR.MINOR versioning scheme. Use `main` for the latest development version. Use `x.y` 
  for release branches.

## Code formatting

- Indentation: 4 spaces.
- Blank lines: use to separate logical blocks of code.
- Line length: maximum 120 characters.
- Use IntelliJ IDEA default code style for Java.

## Java style

Follow standard Java/Spring Boot conventions except where overridden below.

- Use UTF-8 encoding.
- Use descriptive names for classes, methods, and variables.
- Avoid `var` keyword, prefer explicit types, for consistent, easily-reviewable diffs; unless the line is very long 
  and the type is obvious from the right-hand side.
- Prefer immutability. Avoid mutating shared/external state inside `for-each` loops or `Stream.forEach()`/`peek()`.
  Use `map`/`filter`/`collect` or build a new collection instead.
- Avoid magic numbers and strings; use constants instead.
- Check emptiness and nullness before operating on collections and strings. Use project-internal
  `net.microfalx.lang.StringUtils` and `net.microfalx.lang.ObjectUtils`, not Apache Commons or Guava.
- Comment complex or non-obvious business logic; keep comments clear and concise. For public classes/methods
  exposed as module APIs (`public` types outside `internal`/`impl` packages), use Javadoc instead of a plain
  comment; not required for internal/package-private code.
- Use `@Override` when overriding methods.
- Wrap 2 or more boolean conditions (e.g., in an `if`) into a named boolean variable describing the intent,
  instead of an inline compound expression.
- Prefer early returns; avoid `else` when a return can be used instead.
- Use immutability objects with builder pattern objects for API domain objects based on 
  `net.microfalx.lang.IdentityAware.Builder` or one of the subclasses depending on which subclass of 
  `net.microfalx.lang.IdentityAware` is created. 

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
- Do not add new dependencies; Provide reasons why new dependencies are needed.

## Mappers

 - Use `net.microfalx.bootstrap.model.Mapper` which provides a generic mapping framework for converting between domain 
   objects and DTOs. It supports mapping between different types, including nested objects and collections.
 - Use `net.microfalx.bootstrap.model.Mapper.to` to create a new target object and copy properties from the source object.
 - Use `net.microfalx.bootstrap.model.Mapper.copy` to create copy properties from the source object into an existing target object.
 - Use `net.microfalx.bootstrap.model.Mapper.builder` to create builder and customize the mapping (when the property 
   names are not the same) using `ignoreField`, `ignoreAnnotation`, `mapField` in the builder class.

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

## Web dashboards (DataSet API + custom JS framework)

The UI is server-rendered (Thymeleaf) with a custom plain vanilla JS framework (`Application`, `DataSet`, `Utils`,
`Logger` globals), not React/Vue. A dashboard (grid + view modal + toolbar) is built from a model, a data set (only
for non-JPA models), a controller, an optional view template and optional JS actions.

Where to find examples (all controllers extend `DataSetController` or one of its subclasses):

| Module | Kind | Examples |
|---|---|---|
| `extension/support` | POJO, read-only, live JVM/service data, custom actions | `alert/AlertController` (toolbar actions + JS), `diagnostics/DiagnosticsController` (toolbar dropdown, download, inline/iframe content), `pool/*`, `metric/*`, `store/StoreController`, `executor/ExecutorController`, `misc/LibraryController` |
| `extension/system` | POJO, admin | `database/{Database,Node,Session,Transaction}Controller`, `broker/Broker{Consumer,Producer}Controller`, `restapi/{Client,Audit}Controller` (REST API clients and audit) |
| `extension/security` | JPA, editable, row actions + JS | `user/jpa/UserController` (row menu items, POST actions, `security.js`), `group/jpa/GroupController`, `audit/AuditController` |
| `extension/dos` | JPA, editable | `web/RuleController` (JPA entity with natural id, `updateFields`), `web/AuditController`, `web/RequestCountsController` |
| `ai/web` | JPA, admin | `system/{Provider,Model,Prompt,Chat}Controller` (`canAdd`/`canDelete` flags) |
| `extension/web` | search | `search/SearchController`, `search/support/IndexController` |

### Model

- For non-persistent (POJO) dashboards: a class extending `net.microfalx.bootstrap.dataset.model.IdentityAware<ID>`
  (or `NamedIdentityAware`), with Lombok `@Getter`/`@Setter`/`@ToString` and a `static from(source)` factory that maps
  the service/domain object to the model.
- For database-backed dashboards: a JPA `@Entity` (see "Data set" below). The same annotations apply to its fields.
- Class annotations (`net.microfalx.lang.annotation`): `@Name("Title")` and `@ReadOnly` for dashboards that do not
  support add/edit/delete.
- Field annotations control the grid columns:
  - `@Position(n)` sets the column order (the convention is timestamps at ~100 and more, hidden fields at 200 and more).
    `@Width("80px" | "25%")`, `@Label(value, group)` (group makes a grouped header), `@Description` (tooltip).
  - `@Name` marks the display-name field. `@Visible(false)` keeps a field on the model (usable in the view
    template) but hides it from the grid.
  - `@Formattable(unit = BYTES | MILLI_SECOND | COUNT, elapsed = true, zeroValue = Formattable.NA,
    negativeValue = Formattable.NA, tooltip = Formatters.CreatedAtTooltip.class)`.
  - `@OrderBy(DESC)` sets the default sort. `@CreatedAt`/`@CreatedDate`/`@ModifiedAt`/`@Timestamp` mark time fields.
    `@Timestamp` drives the time filter.
- Prefer `LocalDateTime` and primitive `long` durations (millis) on the model. Convert `ZonedDateTime`/`Duration`
  in `from()`.

### Data set

The data set factory is picked from the model class. There are two options.

**JPA entities (no data set class needed).** If the model is a JPA `@Entity`, `JpaDataSet.Factory` creates the data
set automatically and finds the entity's Spring Data repository. Grid, view, add/edit/clone/delete, filtering and
paging come for free (see `extension/dos` `jpa/Rule` + `web/RuleController`, `extension/security` `user/jpa`).

- Extend one of the `@MappedSuperclass` base classes in `data/jdbc` (`net.microfalx.bootstrap.jdbc.entity`). They
  already map the common columns and carry the dashboard annotations:
  - `surrogate.*`: database-generated numeric primary key (`@GeneratedValue(IDENTITY)`, `T extends Number`). This is
    the usual choice.
  - `natural.*`: application-assigned primary key (any `Serializable`, e.g. a `String` id).
  - Both packages have the same hierarchy. Pick the most specific one that matches the table:
    `IdentityAware` (`id`) → `NamedIdentityAware` (+ `name`, `description`) → `NamedAndTimestampedIdentityAware`
    (+ `created_at`, `modified_at`) → `NamedAndTaggedAndTimestampedIdentityAware` (+ `tags`) →
    `NamedAndOwnedAndTaggedAndTimestampedIdentityAware` (+ `created_by`, `modified_by`). There are also
    `NamedAndTaggedIdentityAware`, `NamedAndOwnedAndTimestampedIdentityAware`, and `TimestampAware`/
    `TaggedTimestampAware` (no id) variants.
  - `tags` is a `String` in `natural.*` but a `Set<String>` (`TagsConverter`) in `surrogate.*`.
- A surrogate-key entity can still have a business key: add a `@Column` field annotated with
  `net.microfalx.lang.annotation.NaturalId` (usually `@Visible(false)`) and make the repository extend
  `net.microfalx.bootstrap.jdbc.jpa.NaturalJpaRepository<T, ID>`, which adds `findByNaturalId(String)`. Fill it in
  the controller's `updateFields` hook when it is derived from other fields (see `dos/web/RuleController`).
- Make the repository also implement `JpaSpecificationExecutor<T>`. Without it the grid loads, but any filter
  (search box/time range) fails with "repository does not implement JpaSpecificationExecutor".
- Define the table in `resources/sql/TYPE/schema` (see "Database access").

**POJO models (custom data set).** For data that is not in the database (services, JVM state, external APIs):

- `@Provider` class extending `PojoDataSet<M, PojoField<M>, ID>`, with constructor `(DataSetFactory, Metadata)`.
- Override `doFindById(ID)` (returns `Optional`) and `doFindAll(Pageable, Filter)`. Get Spring services with
  `getService(Service.class)` (data sets are not Spring beans). Return `getPage(models, pageable, filter)` and pass
  a mutable list (`Collectors.toList()`), which handles filtering, sorting and paging in memory.
- Usually combined with `@ReadOnly` on the model. Override the persistence methods only if the dashboard must edit
  the data.

### Controller

- `@Controller @RequestMapping("/module/name")`, extending one of:
  - `DataSetController<M, ID>`: available to any authenticated user (subject to navigation roles).
  - `SystemDataSetController<M, ID>` (`extension/web`): adds `@RolesAllowed("admin")`, for system administration.
  - `SecurityDataSetController<M, ID>` (`extension/security`): base class for the security dashboards.
- Annotate with `@DataSet(model = M.class, ...)` and `@Help("module/name")`. Common `@DataSet` attributes:
  - `viewTemplate = "module/view_x"`, `viewClasses = "modal-xl"`, `detailTemplate`;
  - `defaultQuery = "acknowledged = false"`;
  - `timeFilter = false` for data without a time dimension (most JPA configuration tables), `range = {"today"}`;
  - `canAdd`, `canClone`, `canDelete`, `canUpload`, `canDownload`, `addRoles`/`deleteRoles`/`changeRoles`;
  - `trend = true` with `trendFieldNames`.
- For JPA controllers the ID type parameter is the entity's primary key type (e.g. `Integer` for surrogate keys).
- Constructor injection: `super(dataSetService)` plus your services.
- Hooks to override:
  - `beforeView(dataSet, controllerModel, dataSetModel)` adds extra template attributes. `dataSetModel` can be null.
  - `updateToolbar(Toolbar)` adds toolbar buttons. `updateActions(Menu)` adds row action menu items.
  - `download(...)` returns a `Resource` for `GET {id}/download`.
  - `isReadOnly(dataSet)`, `canAdd`, `canDelete`.
- A model with `@ReadOnly` makes the whole data set read-only. The base controller then omits the View/Edit/Delete/
  Download row actions, even with `canDownload = true`. Add needed ones yourself in `updateActions`, e.g.
  `new Item().setAction("dataset.download")...`.
- Base controller routes already in use: `{id}/view`, `{id}/edit`, `{id}/clone`, `{id}/delete`, `{id}/download`,
  `export`, `page`, `trend`. Pick other names for custom endpoints (e.g. `{id}/content`).
- Custom actions are `@ResponseBody` endpoints returning `JsonResponse.success(message)` (attributes can be added
  with `addAttribute`). Use `@PostMapping` for state-changing actions. `DataSet.post` handles CSRF.
- To show content inline (e.g. in an iframe), return `ResponseEntity<InputStreamResource>` with the content type and
  `Content-Disposition: inline`. The base `download` always uses `attachment`.

### Toolbar, menus and actions

- Components live in `net.microfalx.bootstrap.web.component`: `Button`, `Item`, `Menu`, `Separator`, `Toolbar`.
  A button opens a dropdown via `setMenu(new Menu().setId("x"))`. Keep the toolbar short: move rarely used actions
  into a dropdown.
- `setAction("ns.name")` fires a JS event. `addParameter(name, value)` values are passed **positionally** as
  arguments to the JS callback (in the order they were added).
- Icons are Font Awesome classes (`fa-solid fa-...`).

### JavaScript

- Put controller actions in `src/main/resources/js/controller/<name>.js` of the module, and register the file in
  the module's `src/main/resources/asset.xml` with
  `<update-asset-bundle id="controller"><asset path="<name>.js" type="script"/></update-asset-bundle>`
  (see `extension/security`).
- Pattern:
  ```js
  Application.bind("ns.action", function (param1) {
      DataSet.post("action", {param: param1}, function (json) {
          Application.showInfoAlert("Title", json.message);
          Utils.defer(DataSet.refresh, 2000);
      }, {dataType: "json"});
  });
  ```
- `DataSet.get/post(path, params, callback, options)` resolve `path` relative to the controller and include the
  current request parameters. Other helpers: `DataSet.getId()` (selected row), `DataSet.refresh()`,
  `Application.question(title, text, onYes)` for confirmations, `Application.showAlert`.

### View template and navigation

- The view template (`templates/<viewTemplate>.html`) defines `th:fragment="fields"`. It gets `model` (the data
  set model) plus attributes added in `beforeView`. Follow `support/view_alert.html`: a header box
  (`row border rounded bg-body-secondary`) of `col-md-*` label/value pairs, then sections with
  `dataset-text-mono alert ...` blocks for long text.
- `#formats` helpers: `format`, `formatBytes`, `formatDuration` (millis), `formatDateTime`, `formatElapsed`.
- Build URLs with `th:src="@{/module/name/{id}/content(id=${model.id})}"`. Sandbox iframes that show generated
  HTML (`sandbox="allow-scripts"`).
- Add the menu entry in the module's `src/main/resources/navigation.xml`
  (`<item id action position roles icon/>`).

### Building UI modules

- Always build with `-am` (e.g. `mvn -o -pl extension/support -am compile`). Without it, Maven uses older installed
  jars of sibling modules and fails with confusing "package does not exist" errors.
- The Argus libraries (`net.microfalx.argus`, `../argus`) are snapshots. If the BOM lacks an artifact version or an
  Argus API is missing, the installed snapshot is out of date. Run `mvn -o install -DskipTests` in that Argus module.
- No test covers templates or JS. Verify the UI by running the demo app.
