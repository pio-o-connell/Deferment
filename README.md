# Deferment System

A server-rendered Java/Spring MVC application for managing academic deferral requests. Authorised users can submit deferrals for individual modules or complete programmes, and review, approve, or reject them.

The application also manages supporting data for students, lecturers, programmes, modules, semesters, and registrations.

This document describes the **current local implementation**. It distinguishes implemented behaviour from tested behaviour, outstanding work, and explicitly excluded functionality. See [Business Workflows](#7-business-workflows), [Transaction Test Matrix](#11-transaction-test-matrix), and [Security](#19-security) for important limitations.

## Contents

- [Deferment System](#deferment-system)
  - [Contents](#contents)
  - [1. Project Overview](#1-project-overview)
  - [2. Features](#2-features)
    - [Implemented](#implemented)
    - [Outstanding or limited](#outstanding-or-limited)
  - [3. Architecture](#3-architecture)
  - [4. Spring Root/Child Contexts](#4-spring-rootchild-contexts)
    - [Root `WebApplicationContext`](#root-webapplicationcontext)
    - [Why Spring Security remains in the Root context](#why-spring-security-remains-in-the-root-context)
    - [MVC child `WebApplicationContext`](#mvc-child-webapplicationcontext)
    - [Dependency-injection lifecycle](#dependency-injection-lifecycle)
  - [5. Configuration Responsibilities](#5-configuration-responsibilities)
  - [6. Package Structure](#6-package-structure)
  - [7. Business Workflows](#7-business-workflows)
    - [Module deferral submission](#module-deferral-submission)
    - [Programme deferral submission](#programme-deferral-submission)
    - [Approval and rejection](#approval-and-rejection)
  - [8. Transaction Management](#8-transaction-management)
    - [Propagation used by deferral repositories](#propagation-used-by-deferral-repositories)
    - [Transaction lifecycle](#transaction-lifecycle)
    - [Administrator operations: intended transaction policy](#administrator-operations-intended-transaction-policy)
  - [9. Transaction Guarantees](#9-transaction-guarantees)
    - [Database transaction guarantees](#database-transaction-guarantees)
    - [Business-process limitations](#business-process-limitations)
  - [10. Filesystem and Email Operations](#10-filesystem-and-email-operations)
    - [Supporting PDF storage](#supporting-pdf-storage)
    - [Email notification](#email-notification)
    - [Outstanding improvements](#outstanding-improvements)
  - [11. Transaction Test Matrix](#11-transaction-test-matrix)
    - [Outstanding test coverage](#outstanding-test-coverage)
    - [Original acceptance-case reference](#original-acceptance-case-reference)
  - [12. Failure Handling](#12-failure-handling)
  - [13. Implemented vs Outstanding Functionality](#13-implemented-vs-outstanding-functionality)
  - [14. Database](#14-database)
    - [Deferral status](#deferral-status)
  - [15. Technologies](#15-technologies)
  - [16. Configuration](#16-configuration)
    - [Local database](#local-database)
    - [PostgreSQL](#postgresql)
  - [17. Building and Running](#17-building-and-running)
  - [18. Testing](#18-testing)
    - [Maven package](#maven-package)
    - [DAO integration tests](#dao-integration-tests)
    - [Transaction integration tests](#transaction-integration-tests)
    - [Static configuration test](#static-configuration-test)
    - [Spring application-context diagnostic](#spring-application-context-diagnostic)
    - [Combined configuration checker](#combined-configuration-checker)
    - [Selenium UI tests](#selenium-ui-tests)
    - [Logging](#logging)
  - [19. Security](#19-security)
    - [Authentication and authorisation](#authentication-and-authorisation)
    - [Security limitations and outstanding work](#security-limitations-and-outstanding-work)
  - [20. Not Implemented / Out of Scope](#20-not-implemented--out-of-scope)
  - [21. Known Limitations](#21-known-limitations)
  - [22. Project Structure](#22-project-structure)
  - [23. Author](#23-author)
  - [Repository](#repository)

---

## 1. Project Overview

The application provides:

- JDBC-backed Spring Security form login
- Module deferral submission
- Programme deferral submission
- PDF supporting-document upload
- Deferral listing and filtering
- Approval and rejection of pending deferrals
- Student, lecturer, module, and programme administration
- Admissions email notifications
- JSP views composed with Apache Tiles

The project remains a conventional Java 8 Spring MVC 4, Spring JDBC, JSP, JSTL, and Apache Tiles application. It is not a Spring Boot, REST, JPA, SPA, or microservices application.

---

<a id="features"></a>

## 2. Features

### Implemented

- Form-based authentication through the `users` and `user_roles` tables
- Student and module selection for module deferrals
- Student and programme selection for programme deferrals
- Supporting PDF upload
- Download of an uploaded deferral form by ID
- Admission notification email initiation
- Deferral filtering by student, module, programme, or coordinator
- Conditional approval/rejection decision updates
- Decision lecturer and optional rejection reason on individual deferrals
- Rollback cleanup for newly written supporting documents
- CRUD workflows for students, lecturers, modules, and programmes
- Shared JSP layout through Apache Tiles
- Embedded Tomcat launch support
- HSQLDB-based DAO and transaction integration testing
- Selenium browser-test classes
- Spring Root/child context diagnostics

### Outstanding or limited

- Crash-safe coordination of PDF files and database commits
- Durable email retries
- MySQL concurrency verification
- Full administrator URL enforcement
- A separate decision-history schema
- A document-reference database table
- Stronger upload-content validation

---

<a id="architecture"></a>

## 3. Architecture

The application uses two Spring `WebApplicationContext` instances:

```mermaid
flowchart LR
    HTTP[HTTP request] --> Filter[Spring Security filter]

    subgraph Root["Root WebApplicationContext / Parent"]
        Security[Spring Security]
        DataSource[DataSource]
        TransactionManager[DataSourceTransactionManager]
        Service[Service beans]
        Repository[Repository / DAO beans]
        Mail[JavaMailSender / Mail]
        Database[(Relational database)]

        Security -. uses .-> DataSource
        Service --> Repository
        Repository --> DataSource
        TransactionManager --> DataSource
        DataSource --> Database
    end

    subgraph Child["MVC WebApplicationContext / DispatcherServlet Child"]
        Dispatcher[DispatcherServlet]
        Controller[Controllers]
        Validation[Validation]
        ModelAndView[ModelAndView]
        Tiles[Apache Tiles views]

        Dispatcher --> Controller
        Controller --> Validation
        Controller --> ModelAndView
        ModelAndView -->|resolves| Tiles
    end

    Filter --> Security
    Security --> Dispatcher
    Root -. parent of .-> Child
    Controller -. injects and calls .-> Service
```

![Spring MVC request flow, application contexts and beans](./Architecture-contexts-and-beans.png)


`diagnostic-results.png` is a pre-refactor baseline showing the original context-registration problem, not evidence of the current configuration. See the [context diagnostic](#spring-application-context-diagnostic) for the current checks.

The MVC flow resolves a returned `ModelAndView` through Apache Tiles and renders the configured JSP view.

---

## 4. Spring Root/Child Contexts

### Root `WebApplicationContext`

Loaded by `ContextLoaderListener` from `root-context.xml` and `spring-security.xml`, the Root context contains:

- `DataSource`
- `DataSourceTransactionManager`
- `TransactionTemplate`
- JavaMail infrastructure
- The explicitly registered `com.defermentsystem.domain.Mail` bean
- Repository/DAO beans
- Service beans
- Spring Security authentication and filter-chain beans

<a id="component-scanning"></a>

The Root context scans:

- `com.defermentsystem.service`
- `com.defermentsystem.repository`

It does not scan controller packages.

### Why Spring Security remains in the Root context

`DelegatingFilterProxy` resolves `springSecurityFilterChain` in the Root context by default. JDBC authentication also needs the Root context's `dataSource`, so `spring-security.xml` is loaded there.

### MVC child `WebApplicationContext`

The `mvc-dispatcher` `DispatcherServlet` loads `mvc-dispatcher-servlet.xml` and creates the MVC child context. It contains:

- Spring MVC controllers
- MVC annotation handling
- Bean validation
- Apache Tiles configuration
- Multipart upload handling
- Static resource handling

The child context scans only `com.defermentsystem.controllers`.

<a id="parentchild-bean-visibility"></a>

The child may access inherited beans from the parent. The parent cannot access child beans. Controllers therefore inject services from the parent, and services inject repositories from the parent.

Restricting each context's component scan prevents broad scanning from creating duplicate service and repository instances in both contexts. The Root context should not depend on MVC controllers.

### Dependency-injection lifecycle

1. The Root context creates the `DataSource`.
2. The Root context creates repositories and injects their `DataSource`.
3. The Root context creates services and injects their repositories.
4. The `DispatcherServlet` creates the MVC child context and its controllers.
5. Controllers inject services inherited from the Root context.

`@Autowired` supplies Spring-managed dependencies, such as a service's repository or a controller's service. Ordinary method parameters, including request parameters, do not require `@Autowired`.

---

## 5. Configuration Responsibilities

| File | Responsibility |
|---|---|
| `src/main/webapp/WEB-INF/root-context.xml` | Root infrastructure, component scanning, database configuration, transactions, mail |
| `src/main/webapp/WEB-INF/spring-security.xml` | JDBC authentication, Spring Security filter chain |
| `src/main/webapp/WEB-INF/mvc-dispatcher-servlet.xml` | Controllers, MVC validation, Tiles, multipart handling, static resources |
| `src/main/webapp/WEB-INF/web.xml` | Root context listener, DispatcherServlet, Spring Security filter |
| `src/main/resources/repository-context.xml` | Repository test and command-line `App` context |
| `src/main/webapp/WEB-INF/tiles.xml` | Server-rendered JSP view definitions |

<a id="mvc-annotation-configuration"></a>

`mvc:annotation-driven` explicitly uses the `LocalValidatorFactoryBean` named `validator`.

The MVC child has one annotation-driven declaration; the duplicate plain declaration was removed:

```xml
<mvc:annotation-driven validator="validator"/>
<bean id="validator" class="org.springframework.validation.beanvalidation.LocalValidatorFactoryBean"/>
```

---

<a id="packages"></a>

## 6. Package Structure

| Package | Responsibility |
|---|---|
| `com.defermentsystem.controllers` | Spring MVC controllers and embedded-Tomcat launcher |
| `com.defermentsystem.domain` | Domain models and JDBC row mappers |
| `com.defermentsystem.repository` | DAO interfaces and `JdbcDaoSupport` implementations |
| `com.defermentsystem.service` | Service interfaces and implementations |
| `com.defermentsystem.exceptions` | Form-upload and deferral-state exceptions |

---

<a id="deferral-flows"></a>

## 7. Business Workflows

### Module deferral submission

<a id="student-submission"></a>

Start at `/admin/deferral/addModuleDeferralPage1`, relative to the application context.

The current implementation, represented by `DeferralController.displayModuleDeferralPage2(...)`, performs these steps:

1. Receive student, module, and CRN information.
2. Resolve the module and validate that it corresponds to the supplied CRN.
3. Resolve the student's registered programme and the programme coordinator.
4. Insert the deferral record and retrieve its generated `Def_ID`.
5. Write the uploaded PDF using that ID within the service transaction.
6. After the service transaction commits, initiate an admissions email notification.

The service rolls back the database insert if writing the document fails. It registers rollback cleanup for files created by the submission and does not overwrite existing files. Database transactions do not make filesystem writes atomic: process crashes, unknown commit outcomes, or cleanup failures can still require manual reconciliation.

### Programme deferral submission

Start at `/admin/deferral/addProgrammeDeferralPage1`, relative to the application context.

The current implementation, represented by `DeferralController.displayProgrammeDeferralPage2(...)`, performs these steps:

1. Retrieve modules registered to the student and programme.
2. Resolve the programme coordinator.
3. Create one deferral record for each registered module.
4. Retrieve generated deferral IDs.
5. Save a copy of the supporting PDF for each generated ID.
6. Initiate an admissions email notification.

The controller calls `submitProgrammeDeferral(...)`. Its DAO retrieves all registered modules and inserts each row with a generated ID in one transaction. An empty registration set is rejected. If a file write fails, the transaction rolls back and the service attempts to delete files created by that submission. The separate `createProgrammeDeferral(...)` DAO method uses a batch insert and does not return IDs.

An HSQLDB integration test checks successful creation of both records and files; another injects a file collision and verifies database rollback and cleanup without overwriting the existing file. These tests do not establish crash-safe storage or SMTP delivery.

### Approval and rejection

<a id="lecturer-approval"></a>
<a id="lecturer-rejection"></a>
<a id="concurrent-lecturer-decisions"></a>

`DeferralServiceImpl.decideDeferral(...)` implements:

1. Require the authenticated decision maker to be `L001`, the current business rule.
2. Validate the decision and optional rejection reason, and retrieve the deferral.
3. Verify that the status is pending.
4. Conditionally update the status to `True` or `Rejected`, recording the decision lecturer and optional rejection reason.
5. Require exactly one updated row.

Current statuses are:

| `Approved` value | Meaning |
|---|---|
| `False` | Pending |
| `True` | Approved |
| `Rejected` | Rejected |

The controller obtains the authenticated username and accepts decisions only from `L001`, even when the deferral's programme coordinator is another lecturer. The service enforces the same rule. Decisions are submitted by POST with CSRF protection; the former state-changing GET routes have been removed.

The conditional SQL update requires the row still to be pending, so a stale decision cannot overwrite a completed one. A concurrent HSQLDB test checks that two competing decisions cannot both succeed; this is not proof of MySQL concurrency behaviour. A missing row is translated into a business exception.

An individual decision updates only the selected deferral; it does not remove programme registrations. Legacy bulk approval DAO methods remain but are not exposed through the decision service or controller.

The schema has no:

- Separate decision-history or audit table
- Document-reference table

---

<a id="transaction-management-and-business-workflows"></a>

## 8. Transaction Management

<a id="transaction-architecture"></a>

The application uses `DataSourceTransactionManager` from Spring JDBC. Annotation-driven transactions are enabled in `root-context.xml`:

- `tx:annotation-driven`
- Transaction manager: `transactionManager`
- CGLIB proxying: `proxy-target-class="true"`

The service layer owns logical business transactions. `DeferralServiceImpl` is class-level `@Transactional(readOnly = true)`. Selected mutation methods override this with `@Transactional(rollbackFor = Exception.class)`, including deferral submission and decision methods.

Repositories participate in the surrounding Spring transaction when they use `Propagation.REQUIRED`. `TransactionTemplate` is configured but should only be used for an explicit programmatic transaction need; it is not needed to replace the declarative deferral workflows.

### Propagation used by deferral repositories

| Propagation | Meaning |
|---|---|
| `REQUIRED` | Join the existing service transaction, or create a transaction if one does not exist |
| `REQUIRES_NEW` | Suspend the existing transaction and commit or roll back independently |

Not every `DeferralJdbcDaoSupport` method uses `REQUIRED`.

Current relevant usage includes:

- Module and programme submission operations: `REQUIRED`
- `updateDeferralStatusIfPending(...)`: `REQUIRED`
- `createDeferralGetId(...)` and `getDeferral(...)`: `REQUIRED`
- Many list, lookup, delete, and legacy update methods: `REQUIRES_NEW`

`REQUIRES_NEW` is independent transaction behaviour. All DAO propagation statements must therefore be reviewed before claiming that every larger service workflow is atomic.

### Transaction lifecycle

```mermaid
flowchart LR
    Browser[Browser] --> Controller[Spring MVC Controller]
    Controller --> Proxy[Spring Transaction Proxy]
    Proxy --> Service[Application Service]
    Proxy -. begins and completes transaction .-> Manager[DataSourceTransactionManager]
    Service --> DAO[Repository / DAO]
    DAO --> JdbcTemplate[JdbcTemplate]
    JdbcTemplate --> Database[(Relational Database)]
    Manager -. manages database connection .-> Database
    Service -. file write and rollback cleanup .-> PDF[Filesystem PDF storage]
    Controller -. email after service commit .-> SMTP[SMTP email]
```

The browser/controller layer receives the request and performs the validation present in each flow. The controller calls the service proxy, which establishes the Spring transaction before invoking the service method. The service coordinates the operation; the DAO and `JdbcTemplate` perform database operations. When the transaction boundary is reached, a successful operation commits and an applicable exception causes rollback. DAO methods using `REQUIRES_NEW` instead run in an independent transaction.

The controller must not display a success response after a database operation that failed and propagated an exception.

### Administrator operations: intended transaction policy

This is guidance for identifying business transaction boundaries, not a claim that every existing administrator operation follows it. Some legacy DAO methods use `REQUIRES_NEW`, as noted above.

| Operation | Intended policy |
|---|---|
| Single-row student, lecturer, or module CRUD | An encompassing business transaction is needed when dependent writes are added |
| Programme update with dependent associations | One service transaction across related writes |
| Deferral creation | One service transaction across related database writes |
| Approval or rejection | One service transaction across the decision and any related writes |
| Read-only lists and lookups | Use `readOnly = true` where compatible |

An identified multi-record operation should report failure without leaving a partial update; this requires verification for each operation.

---

## 9. Transaction Guarantees

### Database transaction guarantees

Spring and the relational database provide or help to provide:

- **Atomicity:** all operations in a successfully managed database transaction commit together, or the transaction rolls back.
- **Consistency:** constraints and application validation help preserve a valid database state.
- **Isolation:** concurrent transactions are isolated according to the database isolation level and locking behaviour.
- **Durability:** committed database changes persist according to the relational database engine.

The HSQLDB integration tests demonstrate selected service commit, rollback, filesystem-cleanup and concurrent-decision behaviour within the test environment. They do not demonstrate every workflow, MySQL concurrency scenario, crash scenario, or actual SMTP delivery.

### Business-process limitations

A database transaction does not cover the whole business process. It does not cover:

- PDF validation
- Temporary or final filesystem writes
- SMTP delivery
- Browser response rendering

Therefore the application does not currently provide full ACID-style guarantees across database, filesystem, and email operations. Database atomicity and end-to-end business atomicity must be described separately.

---

<a id="supporting-documents-and-email"></a>

## 10. Filesystem and Email Operations

### Supporting PDF storage

Supporting PDFs are stored below the web application resource directory as:

```text
resources/form/uploadedForms/<Def_ID>.pdf
```

One generated PDF filename is used for a module deferral. Programme deferrals use one PDF for each generated deferral ID. The controller checks the submitted multipart content type for `application/pdf`; it does not verify PDF contents.

The transactional service creates files without overwriting existing files and registers cleanup for files created by the submission if the transaction rolls back. Cleanup failures are logged. Files are written before commit and can be visible before it; a process crash, unknown transaction outcome, or failed cleanup can still leave files and database records inconsistent. The filesystem is not a transactional resource.

### Email notification

The controller calls `Mail.sendModuleDeferralMail(...)` or `Mail.sendProgrammeDeferralMail(...)` after submission. Email delivery is an external SMTP side effect.

The controller sends email only after the transactional submission service returns successfully. SMTP remains outside the transaction: delivery failure is logged and shown as a warning without reversing the committed submission. There is no durable outbox or retry mechanism.

### Outstanding improvements

The following improvements are **not implemented**:

- Stage uploaded documents before final placement
- Prevent final publication when database work fails; coordinate final placement after successful commit
- Recover from process crashes or failed rollback cleanup
- Provide durable email delivery and retries
- Store document metadata in a document-reference table

An HSQLDB test covers a file collision and rollback cleanup; it cannot prove crash recovery or SMTP delivery.

---

<a id="transaction-test-matrix"></a>

## 11. Transaction Test Matrix

`DeferralTransactionTest` uses the real proxied Spring service, transaction manager, HSQLDB database writes and temporary files. It verifies database state through separate `JdbcTemplate` queries after service calls, without a test-level transaction. Test results are limited to the environment in which they run.

| Test | Operation and failure | Verified result | Status |
|---|---|---|---|
| `successfulSubmissionAndApprovalCommit()` | Creates a module deferral, then approves it; no injected failure | Commit: student ID is stored and status becomes `True` | Implemented and tested |
| `laterSubmissionFailureRollsBackEarlierInsert()` | Invokes the real batch-insert programme DAO, then injects an exception | Rollback: no deferrals remain for the test student | Implemented and tested |
| `statusUpdateFailureRollsBackDecision()` | Performs a real status update, then injects an exception | Rollback: status remains `False` | Implemented and tested |
| `duplicateDecisionAndWrongLecturerAreRejected()` | Approves a deferral, then attempts further decisions including one with another lecturer ID | Validation: both later calls throw and status stays `True` | Implemented and tested; pending-record authorisation is covered separately below |
| `programmeSubmissionCommitsEveryRecordAndDocument()` | Submits a programme with two registered modules | Both generated records and their documents exist | Implemented and tested |
| `documentFailureRollsBackProgrammeAndRemovesOnlyNewFiles()` | A later generated document collides with an existing file | Database rows roll back, the new file is removed, and the existing file remains unchanged | Implemented and tested |
| `invalidRegistrationDoesNotCreateSubmission()` | Submits a mismatched module or a programme with no registrations | No deferrals or documents are created | Implemented and tested |
| `onlyL001CanRejectAndOptionalReasonIsStored()` | Attempts a pending decision as another lecturer, then rejects as L001 | Unauthorised decision is rejected; lecturer and optional reason are stored | Implemented and tested |
| `concurrentDecisionsCannotBothSucceed()` | Competing approval and rejection on HSQLDB | Exactly one succeeds and its status and lecturer are stored | Implemented and tested on HSQLDB |

### Outstanding test coverage

| Capability | Status |
|---|---|
| Successful programme submission and generated records | Tested for a two-module programme |
| Checked-exception rollback | File-collision `IOException` rollback tested; other checked failures not exhaustively tested |
| Nonexistent deferral decision | Missing row is translated to a business exception; dedicated test outstanding |
| Wrong lecturer deciding a pending deferral | L001-only policy tested at service and controller levels |
| Dedicated rejection success test | Optional reason and no-reason rejection tested |
| Rejection failure after an earlier database write | Outstanding |
| Concurrent approval/rejection attempts | Tested on HSQLDB; MySQL-specific verification outstanding |
| Concurrent duplicate decisions | Dedicated same-decision test outstanding |
| Document persistence success/failure | Programme success and collision/rollback tested; crash recovery outstanding |
| Email delivery success/failure | Controller failure handling tested with a stub; actual SMTP delivery and durable retry outstanding |

### Original acceptance-case reference

The original README grouped the desired scenarios under the labels below. These are acceptance goals, not blanket claims that the cases passed. The implemented tests and their exact scope are listed above.

| Cases | Acceptance goal | Current evidence or gap |
|---|---|---|
| S1 | Successful module and programme submissions commit their records | Module database submission and programme submission with documents tested |
| S2-S3 | A failure after a submission write rolls back earlier writes | Batch DAO failure and programme document-collision rollback tested |
| S4 | A later document-reference write failure rolls back earlier writes | Not applicable to the current schema: no document-reference table |
| S5 | A checked exception rolls back submission | Document-collision `IOException` tested |
| A1 | Successful approval commits the decision | Module deferral approval tested |
| A2-A3 | Failure during or after a status write rolls back the decision | Injected failure after a status update tested |
| A4 | A decision on a missing deferral makes no changes | Missing-row exception translated; dedicated test outstanding |
| A5-A6 | Duplicate or conflicting decisions do not change an already-decided record | Sequential and concurrent conflicting attempts tested on HSQLDB |
| R1-R6 | Rejection succeeds or rolls back fully on staged failures | Rejection success tested; dedicated rejection failure injection outstanding |
| Admin | Failure after an earlier write in a multi-record administrator operation rolls back all writes | Requires an identified operation and a dedicated test |

Test-level `@Transactional` must not conceal the service proxy's commit or rollback behaviour. Independent database verification is important because it demonstrates that an operation really committed or rolled back in the database rather than only returning a method result or retaining test-runner transaction state.

---

<a id="failure-handling"></a>

## 12. Failure Handling

Service methods that are mutation workflows use `rollbackFor = Exception.class` so checked business failures can also mark the transaction for rollback.

`InvalidDeferralStateException` represents invalid or conflicting deferral decisions. The controller catches decision failures and adds an error message to the model.

Controllers should catch only appropriate request/business exceptions, add diagnostic information to the model or logs, avoid exposing database details, and never display success after rollback.

Submission validation, database and file errors return an error view and do not send the admissions notification. SMTP failure after commit is logged and shown as a warning; the committed deferral is not rolled back. Rollback cleanup is best effort, not a substitute for transactional filesystem storage.

---

## 13. Implemented vs Outstanding Functionality

| Capability | Status |
|---|---|
| Spring MVC application | Implemented |
| Root/child application contexts | Implemented |
| JDBC persistence | Implemented |
| JDBC-backed Spring Security | Implemented |
| Module deferral submission | Implemented |
| Programme deferral submission | Implemented |
| Supporting PDF upload | Implemented |
| Admissions email notification | Implemented |
| Service-level transactions | Implemented for selected workflows |
| Conditional decision updates | Implemented |
| Approval and rejection states | Implemented |
| Decision authorisation | Only authenticated `L001` can decide; this is a fixed policy, not coordinator-based ownership |
| Failure-injection rollback tests | Implemented for selected workflows |
| Dedicated concurrency tests | Conflicting decisions tested on HSQLDB; MySQL testing outstanding |
| Checked-exception rollback tests | Document-collision rollback tested |
| Atomic PDF/database coordination | Not fully atomic; rollback cleanup implemented |
| Email processing | Sent after service commit; durable retries outstanding |
| Rejection-reason persistence | Optional reason stored on the deferral |
| Decision-history audit trail | Not implemented |
| Separate document-reference table | Not implemented |
| Full administrator URL enforcement | Outstanding |

---

<a id="database"></a>

## 14. Database

Run `DatabaseSetup.sql` to create the MySQL schema.

For an existing MySQL or PostgreSQL database, back it up and apply `add-deferral-decision-details.sql` **once** before running the updated application. It adds nullable `Decision_Lect_ID` and `Rejection_Reason` columns; existing decisions remain without recorded details. Do not apply it to a new database already created with the updated schema. The migration is not run automatically.

The principal tables are:

| Table | Responsibility |
|---|---|
| `deferrals` | Deferral request, coordinator, module, status, decision lecturer, and optional rejection reason |
| `student` | Student records |
| `lecturer` | Lecturer records |
| `modules` | Module and CRN information |
| `programme` | Programme and coordinator |
| `registration` | Student/module/programme associations |
| `semester` | Semester records |
| `users` | Login accounts |
| `user_roles` | Spring Security roles |

Supporting PDFs are not stored in these tables. There is no document-reference table.

### Deferral status

The `Approved` column is a bounded string in the current MySQL setup and in HSQLDB test setup:

- `False`: pending
- `True`: approved
- `Rejected`: rejected

The test schema uses `VARCHAR_IGNORECASE(10)`; the MySQL setup uses `VARCHAR(10)`.

The HSQLDB test schema is defined in `src/test/resources/test-schema.sql`. It includes the application tables and authentication tables needed by the integration tests, but it does not reproduce every MySQL-specific engine or foreign-key characteristic.

In particular:

- HSQLDB uses an identity definition for `Def_ID`.
- MySQL uses a composite primary key for deferrals and includes a deferral-to-module foreign key; the HSQLDB test table uses `Def_ID` as its sole primary key and omits that foreign key.
- MySQL semester dates use enumerated values; the HSQLDB schema uses bounded strings.
- HSQLDB status comparison is case-insensitive because `VARCHAR_IGNORECASE` is used.
- MySQL is configured with `InnoDB` for the production-style schema.
- MySQL foreign-key and locking behaviour may differ from HSQLDB.

MySQL concurrency and locking behaviour therefore should not be assumed to have been fully proven solely by HSQLDB tests.

---

<a id="technologies"></a>

## 15. Technologies

| Area | Technology |
|---|---|
| Language/runtime | Java 8 |
| Web framework | Spring MVC 4.0.0 |
| Security | Spring Security 3.2.5 |
| Persistence | Spring JDBC / `JdbcTemplate` / `JdbcDaoSupport` |
| Transaction management | Spring `DataSourceTransactionManager` |
| View layer | JSP, JSTL, Apache Tiles 3.0.1 |
| Validation | Bean Validation 1.1 / Hibernate Validator 5.0.1 |
| Local database | MySQL 5.x; MySQL connector 5.1.6 |
| Cloud database | PostgreSQL through `DATABASE_URL`; JDBC driver 9.4.1212 |
| Test database | HSQLDB 2.0.0 |
| Testing | JUnit 4.11, DBUnit 2.4.9, Spring Test DBUnit 1.1.0, Selenium 4.13.0, Log4j 1.2.12 |
| Embedded server | Apache Tomcat 8.5.23 |
| Mail | JavaMail 1.4.7 |
| File upload | Apache Commons FileUpload 1.3.1 |
| Build | Maven 3, WAR packaging |

---

<a id="configuration"></a>

## 16. Configuration

<a id="spring-application-context-configuration"></a>

Spring application-context responsibilities are listed in [Configuration Responsibilities](#5-configuration-responsibilities).

<a id="local-mysql"></a>

### Local database

Create an ignored `src/main/resources/prop.properties` file using environment-appropriate values:

```properties
jdbc.driverClassName=com.mysql.jdbc.Driver
jdbc.url=jdbc:mysql://localhost:3307/spring_project
jdbc.username=<local-database-user>
jdbc.password=<local-database-password>
```

Do not commit database credentials or SMTP credentials.

<a id="heroku-postgresql"></a>

### PostgreSQL

`HerokuDataSourceFactory` constructs a PostgreSQL JDBC data source when `DATABASE_URL` is set. The environment variable takes precedence over local properties.

The expected format is `postgres://user:password@host:port/database`. Set it in the deployment environment; a local `prop.properties` file is not required when it is supplied.

---

<a id="building-and-running"></a>

## 17. Building and Running

Build the WAR:

```powershell
mvn clean package
```

The default build excludes browser UI tests through the Maven Surefire configuration.

To build without running tests, use `mvn clean package -DskipTests`.

Run with an embedded server after building:

```powershell
java -cp "target/SpringWebProject/WEB-INF/classes;target/SpringWebProject/WEB-INF/lib/*" com.defermentsystem.controllers.HerokuMain
```

The README command may require classpath separators or deployment-container classloading to match the local Java/Maven environment.

Alternatively, deploy `target/SpringWebProject.war` to a Servlet 3.1-compatible container, such as standalone Tomcat 8.5.

The `Procfile` launches `HerokuMain`, which uses `$PORT` or port `8080` and serves the WAR unpacked during Maven packaging.

---

<a id="testing"></a>

## 18. Testing

<a id="running-the-tests"></a>

### Maven package

```powershell
mvn clean package
```

This performs the default build and excludes browser UI tests.

### DAO integration tests

```powershell
mvn "-Dtest=*JdbcDaoSupportTest" test
```

DAO integration tests create their in-memory HSQLDB tables from `src/test/resources/test-schema.sql`, and DBUnit loads the test dataset before each test. An example is `DeferralJdbcDaoSupportTest`. The suite also contains individual unit/smoke tests, such as `HeadlessChromeDriverTest`; those do not establish database transaction behaviour.

### Transaction integration tests

```powershell
mvn "-Dtest=*Transaction*Test" test
```

This runs `DeferralTransactionTest`, including:

- `successfulSubmissionAndApprovalCommit`
- `laterSubmissionFailureRollsBackEarlierInsert`
- `statusUpdateFailureRollsBackDecision`
- `duplicateDecisionAndWrongLecturerAreRejected`
- `programmeSubmissionCommitsEveryRecordAndDocument`
- `documentFailureRollsBackProgrammeAndRemovesOnlyNewFiles`
- `invalidRegistrationDoesNotCreateSubmission`
- `onlyL001CanRejectAndOptionalReasonIsStored`
- `concurrentDecisionsCannotBothSucceed`

`DeferralControllerTest` covers failure handling and MVC routes; `DeferralDecisionSecurityTest` checks the decision filter chain and CSRF token handling.

### Static configuration test

```powershell
mvn "-Dtest=StaticConfigurationTest" test
```

<a id="spring-webapplicationcontext-diagnostic"></a>
<a id="diagnostic-tools"></a>

### Spring application-context diagnostic

```powershell
mvn "-Dtest=WebApplicationContextDiagnosticTest" test
```

The diagnostic creates the real Root context and the conventional `mvc-dispatcher` child context. It verifies bean ownership, context separation, dependency injection, MVC mappings, and transaction proxies. It also prints bean inventories and handler mappings to the test output.

Configuration diagnostics prove configuration and proxy structure. They do not prove successful business transactions or end-to-end browser behaviour.

<a id="spring-configuration-checker"></a>

### Combined configuration checker

```powershell
.\Check-Deferment-Configuration.ps1
```

The checker runs `StaticConfigurationTest` and `WebApplicationContextDiagnosticTest` without starting Tomcat. `PASS` denotes a successful check, `FAIL` a required failure, and `WARNING` an item to review. The script exits nonzero when Maven or a required check fails. Maven writes build output under `target/`; the Root context needs local database configuration or `DATABASE_URL` to initialise. The semester repository has no transactional methods and is reported as a warning rather than a required transaction proxy.

### Selenium UI tests

```powershell
mvn -Pui-tests clean package
```

Selenium tests require:

- A running application at the URL expected by the tests, `http://localhost:8080/SpringWebProject/`
- A dedicated MySQL test database configured through `prop.properties`: DBUnit setup modifies its data
- Chrome; the shared helper uses Selenium Manager for a matching driver

The browser test classes are `DeferralTest`, `LecturerTest`, `LoginTest`, `ModuleTest`, `ProgrammeTest`, and `StudentSeleniumTest`; the shared helper runs Chrome headlessly.

The UI tests are excluded from the default Maven build. The shared `HeadlessChromeDriverTest` is a browser setup smoke test and runs in the default build. Existing UI tests have known issues including click interception, jQuery Mobile selects, and validation-message checks.

The Selenium suite is not evidence that all transaction scenarios passed.

### Logging

Log4j settings are in `src/main/resources/log4j.properties` (INFO to stdout) and `src/test/resources/log4j.properties` (WARN to stderr, with known DBUnit warnings suppressed). CGLIB proxy warnings are routed to `cglib-proxy-warnings.log` in the application's working directory, or `target/cglib-proxy-warnings.log` during tests. JavaMail debug output is disabled. A diagnostic result establishes only the checks actually run; the retained pre-refactor image is not a current test result.

---

<a id="security-notes"></a>

## 19. Security

### Authentication and authorisation

Authentication and authorisation are separate concerns:

- Authentication verifies the username/password in `users`.
- Authorisation assigns roles from `user_roles`.

The intended `/admin/**` administrator restriction is **not currently active**: its `intercept-url` entry in `spring-security.xml` is commented out.

The `/admin/deferral/modify/**` decision paths require authentication, and the controller and service limit decisions to `L001`. This is a fixed decision-maker rule, not coordinator-based ownership.

The other `/admin/**` paths have no active rule requiring authentication or `ROLE_ADMIN`. The login mechanism alone must not be treated as access protection; full administrator URL enforcement remains outstanding.

CSRF protection is explicitly enabled for `/admin/deferral/modify/**` only. It is not enabled for the default security chain; other forms and state-changing routes still need security review.

### Security limitations and outstanding work

- Externalise SMTP configuration and credentials
- Rotate any exposed SMTP credentials
- Keep database credentials outside source control
- Replace demonstration credentials before production use
- Do not treat current sample credentials as production credentials
- Strengthen PDF validation beyond the submitted content type
- Inspect file names and file content where stronger upload enforcement is required

The repository README intentionally does not reproduce passwords or credentials.

---

## 20. Not Implemented / Out of Scope

The application is intentionally not migrating to a REST or modern SPA architecture.

The following are outside the current implementation scope:

- REST APIs
- Spring Boot migration
- JPA/Hibernate migration
- Microservices
- A single-page application
- React
- Angular
- Another frontend framework
- Stripe integration
- Payment processing
- Payment gateways
- Automated payment collection

The existing JSP, JSTL, Apache Tiles, Spring MVC, Spring JDBC, and embedded WAR architecture are preserved.

---

<a id="limitations"></a>

## 21. Known Limitations

- Some legacy DAO methods use `REQUIRES_NEW`.
- Several unrelated CRUD DAO methods may commit independently.
- PDF rollback cleanup is best effort and not crash-safe; SMTP delivery is not durable.
- The `/admin/**` role restriction is not active.
- Decision authorisation is fixed to `L001`, not configurable or coordinator-based.
- The README and tests should not be used to claim complete MySQL concurrency verification.
- Selenium/UI test dependencies require a running application and dedicated database.
- Older Java/Spring/JDBC/Tomcat versions are in use.
- The application may require Java 8 for compatibility.

---

<a id="project-structure"></a>

## 22. Project Structure

```text
Deferment/
├── pom.xml
├── DatabaseSetup.sql
├── Procfile
├── src/
│   ├── main/
│   │   ├── java/com/defermentsystem/
│   │   │   ├── controllers/        # MVC controllers and embedded-Tomcat launcher
│   │   │   ├── domain/             # Domain models and row mappers
│   │   │   ├── exceptions/         # Application exceptions
│   │   │   ├── repository/         # DAO interfaces and JDBC implementations
│   │   │   └── service/            # Service interfaces and implementations
│   │   ├── resources/
│   │   │   ├── repository-context.xml # Repository and command-line context
│   │   │   ├── databaseEntries.xml     # DBUnit seed data
│   │   │   └── prop.properties        # Local database settings, if configured
│   │   └── webapp/
│   │       ├── resources/          # Static resources and uploaded forms
│   │       └── WEB-INF/
│   │           ├── jsp/            # JSP views
│   │           ├── root-context.xml
│   │           ├── mvc-dispatcher-servlet.xml
│   │           ├── spring-security.xml
│   │           └── tiles.xml
│   └── test/
│       ├── java/com/defermentsystem/
│       └── resources/              # Including the HSQLDB test schema
└── target/                         # Maven build output
```

---

<a id="author"></a>

## 23. Author

Pio O'Connell

## Repository

[Deferment on GitHub](https://github.com/pio-o-connell/Deferment)
