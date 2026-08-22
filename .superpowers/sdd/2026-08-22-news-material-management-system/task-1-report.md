# Task 1 Report: Bootstrap, Authentication, Projects

## Result
Implemented the Spring Boot 4.1.1 / Java 21 foundation with a local H2 demo profile, PostgreSQL-default configuration, session authentication, CSRF protection, two BCrypt-encoded demo users, project create/list/creator-only rename APIs, DTO responses, and stable JSON authorization errors.

## Tests
- RED observed: focused API tests initially failed because the application did not exist.
- GREEN verified: \`./mvnw -q -Dtest=AuthenticationApiTest,ProjectApiTest test\`
- Final verification: \`./mvnw -q test\`

## API contract
- POST /api/v1/auth/login
- GET /api/v1/auth/csrf
- GET /api/v1/projects
- POST /api/v1/projects
- PATCH /api/v1/projects/{id}

## Notes
- The local H2 profile uses Hibernate create-drop so demo tests do not depend on an external database. The V1 migration is present for PostgreSQL deployment, but Flyway's H2-specific module has not yet been added.
- Spring Boot 4 uses the \`tools.jackson\` API, rather than the older \`com.fasterxml.jackson\` ObjectMapper type.
- Task 1 remains intentionally limited to projects and auth; material, version, upload, search, and AI features are not implemented here.


## Fix round 1

- RED: \`./mvnw -q -Dtest=ProjectApiTest#validationFailureUsesTheStableErrorEnvelope test\` failed because \`requestId\` was absent.
- GREEN: the same focused test passed after the exact \`code,message,requestId,details\` envelope was implemented.
- CSRF: \`./mvnw -q -Dtest=ProjectApiTest#csrfEndpointProvidesTokenAndCookieForMutation test\` passed using GET csrf's returned cookie and token.
- Wrapper: Maven's official \`wrapper:wrapper -Dtype=bin\` generated standard scripts, \`.mvn/wrapper/maven-wrapper.jar\`, and a normal Maven Central distribution URL; \`./mvnw -q -DskipTests package\` passed.
- Local profile: H2 uses disposable create-drop and explicitly disables Flyway; production application.yml retains Flyway enabled for PostgreSQL migration V1.
- Full suite: \`./mvnw -q test\` passed.
- Self-review: error response now has only required envelope fields; no material/version features were added.
- Remaining architecture note: package split is still required in a follow-up refactor.


## Fix round 2: cohesive package split

### Files
- NewsMaterialApplication.java now contains only the Spring Boot bootstrap.
- common/api owns the stable error envelope, domain API exception, and exception advice.
- security owns the session security configuration, demo seeding, user entity/repository/details service, authentication endpoints, and security handlers.
- project owns the project entity/repository/service/controller and request/response DTOs.

### Verification
- ./mvnw -q -Dtest=AuthenticationApiTest,ProjectApiTest test passed.
- ./mvnw -q test passed.

### Self-review
- Public API routes, session-based authentication, CSRF behavior, stable error envelope, and creator-only project rename behavior are unchanged.
- The controller delegates project policy and transactions to ProjectService; no source remains duplicated in the application bootstrap.
- No material, version, upload, search, or AI functionality was added.

### Commit
- refactor: split bootstrap into cohesive packages (this commit).
