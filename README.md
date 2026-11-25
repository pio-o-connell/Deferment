# Deferment System

A Spring MVC web application for managing academic deferral requests. Authorised users can submit, review, approve, or reject deferrals for individual modules or entire programmes. Supporting entities — students, lecturers, programmes, modules, semesters, and registrations — are all managed through the same application.

---

## Features

- Form-based login with JDBC-backed Spring Security
- Create module or programme deferrals through guided multi-step flows
- Upload a supporting PDF document for each deferral request
- List and filter deferrals by student, module, programme, or coordinator
- Approve or reject individual deferral records
- Download the uploaded deferral form by ID
- Full CRUD management for students, lecturers, modules, and programmes
- Email notifications sent to admissions on deferral submission
- Shared page layouts via Apache Tiles (header/footer on every page)

---

## Architecture

The application uses two Spring `WebApplicationContext` instances: a shared Root context and a request-handling MVC Child context.

### Root WebApplicationContext

The Root context is the shared application infrastructure. It is configured by `root-context.xml` and `spring-security.xml` and contains:

- The `DataSource`
- The transaction manager
- `TransactionTemplate`
- `JavaMailSender`
- The `Mail` component
- Repository/DAO beans
- Service beans
- Spring Security configuration

The Root context scans only `com.defermentsystem.service` and `com.defermentsystem.repository`. It does not scan all application packages or depend on MVC controllers.

### MVC Child WebApplicationContext

The Child context is created by the `DispatcherServlet` and is configured by `mvc-dispatcher-servlet.xml`. It contains:

- Spring MVC controllers
- MVC annotation handling
- Validation
- View resolution
- Apache Tiles configuration
- Multipart request handling
- Static resource handling

The Child context scans only `com.defermentsystem.controllers`.

### Parent–Child bean visibility

The Root context is the Parent and the `DispatcherServlet` MVC context is the Child:

- The Child can access beans defined in the Parent.
- The Parent cannot access beans defined in the Child.
- Controllers can therefore inject services from the Root context.
- Services can inject repositories/DAOs from the Root context.
- The Root context should not depend on MVC controllers.

Keeping services and repositories in the Root context and controllers in the Child prevents broad component scanning from creating duplicate service and repository instances in both contexts.

```mermaid
flowchart LR
    HTTP[HTTP request] --> Filter[Spring Security filter]

    subgraph Root["Root WebApplicationContext (Parent)"]
        Security[Spring Security]
        Service[Service bean]
        Repository[Repository / DAO bean]
        DataSource[DataSource]
        Database[(Database)]

        Security -. uses .-> DataSource
        Service --> Repository
        Repository --> DataSource
        DataSource --> Database
    end

    subgraph Child["MVC WebApplicationContext (Child)"]
        Dispatcher[DispatcherServlet]
        Controller[Controller]
        ModelAndView[ModelAndView]
        View[Tiles/JSP view resolution]

        Dispatcher --> Controller
        Controller --> ModelAndView
        ModelAndView --> View
    end

    Filter --> Security
    Security --> Dispatcher
    Root -. parent of .-> Child
    Controller -. injects and calls .-> Service
```

The MVC flow resolves the returned `ModelAndView` through Apache Tiles and ultimately renders the configured JSP view.

### Configuration responsibilities

| File | Responsibility |
|---|---|
| `root-context.xml` | Root context infrastructure, service/repository scanning, DataSource, transaction configuration and Mail |
| `spring-security.xml` | Root context security configuration and JDBC authentication |
| `mvc-dispatcher-servlet.xml` | MVC Child context, controller scanning, annotation handling, validation, view resolution, Tiles and multipart configuration |
| `repository-context.xml` | Classpath configuration for repository tests and the `App` command-line entry point |

![Proposed and implemented Spring application-context separation](./proposed-config-change.jpeg)

`diagnostic-results.png` is retained as a **pre-refactor baseline**. It records the original context-registration problem; it is not evidence of the current post-refactor result. The current configuration is checked by `WebApplicationContextDiagnosticTest` (see [Testing](#testing)).

In `web.xml`, `ContextLoaderListener` loads the Root context. The `DispatcherServlet` is named `mvc-dispatcher`, so it automatically looks for `/WEB-INF/mvc-dispatcher-servlet.xml` as its MVC Child context.

`web.xml` loads `/WEB-INF/root-context.xml` and `/WEB-INF/spring-security.xml` into the Root context. The MVC file is loaded only by the `mvc-dispatcher` Child context, keeping controllers and MVC infrastructure out of the Root.

### Why Spring Security remains in the Root context

`DelegatingFilterProxy` looks up the `springSecurityFilterChain` bean in the Root `WebApplicationContext` by default. The JDBC authentication configuration also requires access to the Root context's `dataSource` bean. Therefore, `spring-security.xml` remains in the Root context.

### Component scanning

- `root-context.xml` scans only:
  - `com.defermentsystem.service`
  - `com.defermentsystem.repository`
- `root-context.xml` explicitly registers the `com.defermentsystem.domain.Mail` bean.
- `mvc-dispatcher-servlet.xml` scans only:
  - `com.defermentsystem.controllers`

### MVC annotation configuration

The MVC Child context uses one annotation-driven configuration, with the validator explicitly assigned:

```xml
<mvc:annotation-driven validator="validator" />
```

A `LocalValidatorFactoryBean` is defined in the MVC Child context:

```xml
<bean id="validator"
      class="org.springframework.validation.beanvalidation.LocalValidatorFactoryBean" />
```

The duplicate plain `<mvc:annotation-driven/>` declaration has been removed; the validator is configured once through the explicit validator attribute.

### Dependency-injection lifecycle

The contexts work together in this order:

1. The Root context creates the `DataSource`.
2. The Root context creates repository/DAO beans and injects the `DataSource`.
3. The Root context creates service beans and injects their repository dependencies.
4. The `DispatcherServlet` creates the MVC Child context.
5. The Child context creates controllers.
6. Controllers inject and use service beans from the Parent context.

`@Autowired` is used for Spring-managed bean dependencies such as repository-to-service or service-to-repository relationships and controller-to-service relationships. Ordinary method parameters, including controller request parameters, do not need `@Autowired`.

### Packages

| Package | Responsibility |
|---|---|
| `com.defermentsystem.controllers` | Spring MVC controllers and the embedded Tomcat launcher |
| `com.defermentsystem.domain` | Domain models (`Deferral`, `Student`, `Lecturer`, `Module`, `Programme`, `Mail`) and JDBC row mappers |
| `com.defermentsystem.repository` | DAO interfaces and `JdbcDaoSupport` implementations, plus `HerokuDataSourceFactory` |
| `com.defermentsystem.service` | Service interfaces and thin delegating implementations |
| `com.defermentsystem.exceptions` | `FormUploadException` for non-PDF uploads |

---

## Deferral Flows

**Module deferral** (`/admin/deferral/addModuleDeferralPage1`)

1. User enters a student ID and selects a module with its CRN.
2. The DAO JOINs `programme` and `registration` to look up the student's coordinator.
3. A row is inserted into `deferrals` and the uploaded PDF is saved to the server under its generated `Def_ID`.
4. An email is sent to admissions confirming the submission.

**Programme deferral** (`/admin/deferral/addProgrammeDeferralPage1`)

1. User enters a student ID and programme ID.
2. The DAO fetches all modules registered for that student and programme, then bulk-inserts one deferral row per module using `batchUpdate`.
3. The uploaded PDF is saved once for each generated deferral ID.
4. An email is sent to admissions, and on approval the student's registrations are removed from the `registration` table.

---

## Technologies

| Concern | Version |
|---|---|
| Java | 8 |
| Spring Framework | 4.0.0.RELEASE |
| Spring Security | 3.2.5.RELEASE |
| Persistence | Spring JDBC (`JdbcTemplate` / `JdbcDaoSupport`) |
| View layer | JSP, JSTL, Apache Tiles 3.0.1 |
| Validation | Hibernate Validator 5.0.1 / Bean Validation 1.1 |
| Build | Maven 3 (WAR — `SpringWebProject.war`) |
| Local database | MySQL 5.x (connector 5.1.6) |
| Cloud database | PostgreSQL 9.4 (Heroku) |
| Embedded server | Apache Tomcat 8.5.23 |
| File upload | Apache Commons FileUpload 1.3.1 |
| Email | JavaMail 1.4.7 |
| Testing | JUnit 4.11, DBUnit 2.4.9, Spring Test DBUnit 1.1.0, HSQLDB, Selenium 4.13.0 (headless Chrome), Log4j 1.2 |

---

## Database

Run `DatabaseSetup.sql` to create the `spring_project` MySQL schema.

| Table | Description |
|---|---|
| `deferrals` | Deferral requests: student, lecturer, programme, module, approval status |
| `student` | Student records |
| `lecturer` | Lecturer records |
| `modules` | Module and CRN information |
| `programme` | Programme and coordinator |
| `registration` | Junction table: student, CRN, programme |
| `semester` | Semester records |
| `users` | Login accounts |
| `user_roles` | Roles/authorities for Spring Security |

Spring Security reads from `users` and `user_roles` via the JDBC user-service configured in `spring-security.xml`.

---

## Configuration

### Spring application-context configuration

The applied separation and responsibilities are described in [Architecture](#architecture). In summary:

- `root-context.xml` belongs to the Root context and defines infrastructure and root-level service/repository scanning.
- `spring-security.xml` belongs to the Root context so `springSecurityFilterChain` and JDBC authentication can use the Root `dataSource`.
- `mvc-dispatcher-servlet.xml` belongs to the MVC Child context and defines controllers, MVC validation, view resolution, Tiles, multipart handling, and static resources.
- `repository-context.xml` is a separate classpath context used by repository tests and `App.java`.

### Local (MySQL)

Create the ignored `src/main/resources/prop.properties` file:

```properties
jdbc.driverClassName=com.mysql.jdbc.Driver
jdbc.url=jdbc:mysql://localhost:3307/spring_project
jdbc.username=root
jdbc.password=yourpassword
```

### Heroku (PostgreSQL)

`HerokuDataSourceFactory` uses the `DATABASE_URL` environment variable (Heroku's standard `postgres://user:pass@host:port/db` format) to construct a PostgreSQL JDBC URL at startup. Set `DATABASE_URL` in the Heroku app's config vars; a local `prop.properties` file is not required on Heroku. If both are present, `DATABASE_URL` takes precedence.

---

## Project Structure

```text
Deferment/
├── pom.xml
├── DatabaseSetup.sql
├── Procfile
│
├── src/
│   ├── main/
│   │   ├── java/com/defermentsystem/
│   │   │   ├── controllers/      # MVC controllers + HerokuMain launcher
│   │   │   ├── domain/           # Domain models + row mappers
│   │   │   ├── exceptions/       # FormUploadException
│   │   │   ├── repository/       # DAO interfaces + JDBC implementations
│   │   │   └── service/          # Service interfaces + implementations
│   │   │
│   │   ├── resources/
│   │   │   ├── repository-context.xml # Datasource + transaction manager (classpath)
│   │   │   ├── databaseEntries.xml # DBUnit seed data
│   │   │   └── prop.properties   # Local MySQL connection settings
│   │   │
│   │   └── webapp/
│   │       └── WEB-INF/
│   │           ├── jsp/           # JSP views grouped by entity
│   │           ├── root-context.xml            # Root infrastructure and component scan
│   │           ├── mvc-dispatcher-servlet.xml # MVC + Tiles config
│   │           ├── spring-security.xml        # Authentication + authorisation
│   │           └── tiles.xml                  # ~30 Tiles view definitions
│   │
│   └── test/
│       └── java/com/defermentsystem/
│
└── target/   # Maven build output
```

---

## Building and Running

**Build the WAR:**

```bash
mvn clean package
```

The default build runs the unit, DAO and configuration tests (no running app or external database needed) and skips the browser UI tests. Add `-DskipTests` to skip all tests.

**Run locally (embedded Tomcat):**

```bash
java -cp target/SpringWebProject/WEB-INF/classes:... com.defermentsystem.controllers.HerokuMain
```

Or deploy `target/SpringWebProject.war` to any Servlet 3.1-compatible container (e.g. standalone Apache Tomcat 8.5.x).

**Heroku:** the `Procfile` launches `HerokuMain`, which starts embedded Tomcat on `$PORT` (default `8080`) and serves the unpacked WAR produced by the Maven Ant unzip step.

---

## Security Notes

- Authentication is JDBC-based: credentials are stored in the `users` table and roles in `user_roles`.
- The `intercept-url` rule restricting `/admin/**` to `ROLE_ADMIN` is currently **commented out** in `spring-security.xml`. Any authenticated user can reach all admin endpoints. Re-enable it before deploying to a production environment.
- Form submission requires Spring Security's CSRF protection, which is enabled by default under `auto-config="true"`.

---

## Testing

The test suite uses JUnit 4, DBUnit with an HSQLDB in-memory database for DAO tests, and Selenium for browser-based UI tests. A sample DAO test is at `src/test/java/com/defermentsystem/repository/DeferralJdbcDaoSupportTest.java`.

The repository tests create their own in-memory HSQLDB tables from `src/test/resources/test-schema.sql`; DBUnit loads the existing dataset before each test. Run them with Java 8:

```powershell
mvn '-Dtest=*JdbcDaoSupportTest' test
```

### Selenium UI tests

The Selenium tests (`DeferralTest`, `LecturerTest`, `LoginTest`, `ModuleTest`, `ProgrammeTest`, `StudentSeleniumTest`) drive **headless Chrome** through the shared `HeadlessChromeDriver` helper, so no browser windows open. Chrome must be installed; Selenium Manager downloads the matching driver.

They are excluded from the default `mvn package` build because they need the application running at `http://localhost:8080/SpringWebProject/` and the MySQL database from `prop.properties`. They use the existing `repository-context.xml`, not the in-memory repository-test database, and their DBUnit setup modifies that database, so use a dedicated test database. To include them:

```bash
mvn -Pui-tests clean package
```

`HeadlessChromeDriverTest` is a quick smoke test of the Chrome setup and runs in the default build. Some UI tests are known to fail (footer click interception, jQuery Mobile selects, validation-message checks).

### Logging

Log4j is configured in `src/main/resources/log4j.properties` (INFO to stdout) and `src/test/resources/log4j.properties` (WARN to stderr, with known-harmless DBUnit warnings suppressed). JavaMail debug output is disabled in `root-context.xml`.

### Spring WebApplicationContext diagnostic

`src/test/java/com/defermentsystem/context/WebApplicationContextDiagnosticTest.java` creates the Root context using the same two XML locations listed in `web.xml`, then initializes a `DispatcherServlet` named `mvc-dispatcher` so Spring loads its conventional MVC Child configuration.

The diagnostic checks the Root/Child parent relationship and local bean registration, verifies that service and repository implementations are not duplicated across the contexts, checks that controllers and MVC handler mappings belong to the Child, and inspects dependency injection and repository transaction proxies. It also prints bean inventories and handler mappings to the test output.

Run this diagnostic independently with:

```bash
mvn -Dtest=WebApplicationContextDiagnosticTest test
```

A successful run means the diagnostic's assertions passed for the configuration loaded by that test. It is separate from the Selenium browser tests and does not, by itself, establish that the complete web application has passed end-to-end testing.

### Spring configuration checker

From the project root, run the PowerShell checker with Maven and Java 8 configured (Maven uses `JAVA_HOME`):

```powershell
.\Check-Deferment-Configuration.ps1
```

The checker runs `StaticConfigurationTest` against the actual Spring XML and web descriptor, then runs `WebApplicationContextDiagnosticTest` to initialise the real Root and MVC Child contexts without a running Tomcat server. It checks context locations, component scans, explicit beans, local bean counts and separation, inherited beans, dependency injection, repository data sources and transaction advice, and MVC handler mappings.

`PASS` means a check succeeded; `FAIL` means a required check failed; `WARNING` highlights something to review that does not necessarily prevent startup. The script returns a non-zero exit code if Maven or a required check fails. The semester repository currently has no transactional methods, so it is reported as a warning rather than required to have a transaction proxy. The checker does not modify configuration or application data, but Maven writes build output to `target/`. A local database configuration or `DATABASE_URL` is still needed for the Root context to start.

---

## Author

**Pio O'Connell**

## Repository

[DefermentSystem on GitHub](https://github.com/pio-o-connell/Deferment.git)
