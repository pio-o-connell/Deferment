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

The Root context is the shared application infrastructure. It is configured by `configuration.xml` and `spring-security.xml` and should contain:

- The `DataSource`
- The transaction manager
- `TransactionTemplate`
- `JavaMailSender`
- Repository/DAO beans
- Service beans
- Spring Security configuration

The Root context should scan only `com.defermentsystem.service` and `com.defermentsystem.repository`. It should not scan all application packages or depend on MVC controllers.

### MVC Child WebApplicationContext

The Child context is created by the `DispatcherServlet` and is configured by `mvc-dispatcher-servlet.xml`. It should contain:

- Spring MVC controllers
- MVC annotation handling
- Validation
- View resolution
- Apache Tiles configuration
- Multipart request handling
- Static resource handling

The Child context should scan only `com.defermentsystem.controllers`.

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
| `configuration.xml` | Root context infrastructure, component scanning for services and repositories, DataSource and transaction configuration |
| `spring-security.xml` | Root context security configuration and JDBC authentication |
| `mvc-dispatcher-servlet.xml` | MVC Child context, controller scanning, annotation handling, validation, view resolution, Tiles and multipart configuration |

![Proposed Spring application-context configuration](./proposed-config-change.jpeg)
![Diagnostic results ](./diagnostic-results.png)

In `web.xml`, `ContextLoaderListener` loads the Root context. The `DispatcherServlet` is named `mvc-dispatcher`, so it automatically looks for `/WEB-INF/mvc-dispatcher-servlet.xml` as its MVC Child context.

The current `web.xml` contains a `contextConfigLocation` entry that lists `mvc-dispatcher-servlet.xml`, `configuration.xml`, and `spring-security.xml`. That configuration currently makes all three files available to the context loaded by `ContextLoaderListener`; it is not the final intended separation described above. Under the proposed arrangement, the Root files would be `configuration.xml` and `spring-security.xml`, while `mvc-dispatcher-servlet.xml` would remain the automatically loaded Child context file. These XML changes are documentation of the intended configuration only and have not been applied.

### Why Spring Security remains in the Root context

`DelegatingFilterProxy` looks up the `springSecurityFilterChain` bean in the Root `WebApplicationContext` by default. The JDBC authentication configuration also requires access to the Root context's `dataSource` bean. Therefore, `spring-security.xml` should remain in the Root context in this proposed arrangement.

### Intended component scanning

The following separation is intended, but is not currently claimed as an applied XML change:

- In `configuration.xml`, restrict the component scan to:
  - `com.defermentsystem.service`
  - `com.defermentsystem.repository`
- In `mvc-dispatcher-servlet.xml`, restrict the component scan to:
  - `com.defermentsystem.controllers`

### MVC annotation configuration

The MVC Child context should use one annotation-driven configuration, with the validator explicitly assigned:

```xml
<mvc:annotation-driven validator="validator" />
```

A `LocalValidatorFactoryBean` should be defined in the MVC Child context:

```xml
<bean id="validator"
      class="org.springframework.validation.beanvalidation.LocalValidatorFactoryBean" />
```

The duplicate plain `<mvc:annotation-driven/>` declaration should not be retained alongside the validator-enabled declaration. This describes the intended XML configuration only; the XML files have not been edited as part of this documentation update.

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
| Testing | JUnit 4.11, DBUnit 2.4.9, Spring Test DBUnit 1.1.0, HSQLDB, Selenium 2.44 |

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

The intended separation and responsibilities are described in [Architecture](#architecture). In summary:

- `configuration.xml` belongs to the Root context and defines infrastructure and root-level service/repository scanning.
- `spring-security.xml` belongs to the Root context so `springSecurityFilterChain` and JDBC authentication can use the Root `dataSource`.
- `mvc-dispatcher-servlet.xml` belongs to the MVC Child context and defines controllers, MVC validation, view resolution, Tiles, multipart handling, and static resources.

### Local (MySQL)

Edit `src/main/resources/prop.properties`:

```properties
db.driver=com.mysql.jdbc.Driver
db.url=jdbc:mysql://localhost:3307/spring_project
db.username=root
db.password=yourpassword
```

### Heroku (PostgreSQL)

`HerokuDataSourceFactory` reads the `DATABASE_URL` environment variable (Heroku's standard `postgres://user:pass@host:port/db` format) and constructs a PostgreSQL JDBC URL at startup. No code change is needed — if `DATABASE_URL` is present, it takes precedence.

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
│   │   │   ├── configuration.xml # Datasource + transaction manager (classpath)
│   │   │   ├── databaseEntries.xml # DBUnit seed data
│   │   │   └── prop.properties   # Local MySQL connection settings
│   │   │
│   │   └── webapp/
│   │       └── WEB-INF/
│   │           ├── jsp/           # JSP views grouped by entity
│   │           ├── configuration.xml          # Datasource config (WEB-INF)
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
mvn clean package -Dmaven.test.skip=true
```

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

---

## Author

**Pio O'Connell**

## Repository

[DefermentSystem on GitHub](https://github.com/pio-o-connell/Deferment.git)
