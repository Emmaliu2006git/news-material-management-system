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

