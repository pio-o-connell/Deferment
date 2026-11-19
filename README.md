# DeferralSystem

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

The application follows a standard layered Spring MVC design.

```
HTTP Request → Spring Security Filter → DispatcherServlet
                                              ↓
                                        @Controller
                                              ↓
                                         @Service
                                              ↓
                                    @Repository (JdbcDaoSupport)
                                              ↓
                                           Database
                                              ↓
                                     ModelAndView → JSP (via Tiles)
```

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

[DeferralSystem on GitHub](https://github.com/pio-o-connell/Java-Spring---DeferralSystem)
