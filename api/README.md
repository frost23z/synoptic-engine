# Synoptic Engine

## Project
Spring Boot 4 modular monolith CRM (MVP of a larger ERP system).
Backend: Kotlin + Spring Boot 4.0.6 + Spring Framework 7 + Hibernate 7
Frontend: Nuxt 4 + TypeScript + Pinia + @nuxt/ui (not started yet)
DB: PostgreSQL with Flyway migrations
Port: 8090

## Package
com.synopticengine.api

## What is built and working
### Shared kernel (shared/)
- BaseEntity — UUID id, createdAt, updatedAt via @CreatedDate/@LastModifiedDate
- AuditableEntity extends BaseEntity — createdBy, updatedBy via @CreatedBy/@LastModifiedBy
- JpaAuditingConfig — AuditorAware<UUID> reads UserPrincipal from SecurityContextHolder
- GlobalExceptionHandler — ProblemDetail (RFC 9457) for all error responses
- PageResponse<T> — wraps Spring Page<T>, strips internal pageable/sort fields
- PasswordConfig — BCryptPasswordEncoder bean

### Identity module (identity/)
- User entity (extends AuditableEntity) — email, passwordHash, firstName, lastName, phone, isActive, deletedAt, ManyToMany roles
- Role entity (extends BaseEntity) — name, description, ManyToMany permissions
- Permission entity (extends BaseEntity) — name, description
- UserRepository extends JpaRepository + JpaSpecificationExecutor
- RoleRepository
- UserSpecs — notDeleted, isActive, hasRole, emailContains, nameContains
- UserService implements IdentityApi — create, deactivate, updateProfile, findAll(search, isActive, role, pageable), findByEmailWithRoles, findCredentialsByEmail, findCredentialsById
- IdentityApi (public interface at module root) — exposes UserSummary and UserCredentials only, never User entity
- UserController — GET /api/users (paginated, filterable, sortable), POST, PUT /{id}, DELETE /{id}

### Auth module (auth/)
- UserPrincipal implements UserDetails — id: UUID, email, authorities
- JwtTokenProvider — generateAccessToken, generateRefreshToken, validateToken, getUserIdFromToken, getAuthoritiesFromToken, isRefreshToken, isAccessToken
- JwtAuthFilter extends OncePerRequestFilter — extracts Bearer token, reconstructs UserPrincipal from JWT claims (no DB call per request)
- SecurityConfig — stateless JWT, CSRF disabled, /auth/** public, everything else authenticated, @EnableMethodSecurity
- AuthService — login, refresh, both using IdentityApi only (not UserService directly — modularity enforced)
- AuthController — POST /auth/login, POST /auth/refresh
- TokenResponse — accessToken, refreshToken, tokenType, userId, email, fullName, authorities

### Database (Flyway migrations V001-V007)

## Key architectural rules — enforce these strictly
1. UUID everywhere — no Long IDs anywhere
2. Never expose JPA entities outside their module — always DTOs
3. Controllers inject one service only — their own module's service
4. Cross-module communication via *Api interfaces at module root only — never direct repository or service injection across modules
5. Services use @Transactional(readOnly = true) at class level, @Transactional on write methods
6. All filterable list endpoints use JpaSpecificationExecutor + Pageable + a *Specs object
7. Paginated endpoints return PageResponse<T>
8. Lookup/dropdown endpoints return List<T> unpaged
9. Soft deletes on: leads, contacts (persons, organizations), quotes, activities, products, users, pipelines, stages — filter deletedAt IS NULL in all queries
10. Status fields are Kotlin enums with @Enumerated(EnumType.STRING) on the entity
11. Spring Modulith is active — module boundaries are verified by ModularityTest. Build fails if boundaries are crossed
12. GlobalExceptionHandler handles all exceptions — never return raw exceptions from controllers
13. Naming: snake_case in DB (auto via CamelCaseToUnderscoresNamingStrategy), camelCase in Kotlin
14. allOpen plugin handles JPA proxy requirements — never write open keyword manually
15. Passwords hashed with BCrypt — never stored or returned in plain text
16. AuditorAware reads UserPrincipal from SecurityContextHolder — never pass actorId manually to service methods

## What needs to be built next (in order)
1. CRM tag module (crm/tag/) — Tag entity already in DB, simplest module, no dependencies
2. CRM contact module (crm/contact/) — Organization, Person entities, ContactsApi interface
3. CRM product module (crm/product/) — Product entity, ProductsApi interface  
4. CRM lead module (crm/lead/) — Lead, Pipeline, Stage, LeadSource, LeadType, LeadsApi interface
5. CRM activity module (crm/activity/) — Activity entity, ActivitiesApi interface
6. CRM quote module (crm/quote/) — Quote, QuoteItem, QuotesApi interface
7. CRM dashboard module (crm/dashboard/) — reads via *Api interfaces only, no direct repo access
8. @PreAuthorize annotations on all controllers using the seeded permissions

## Module structure pattern — every CRM module follows this exactly
crm/
  <module>/
    domain/        — entities (internal, never exposed outside module)
    repo/          — repositories + *Specs (internal)
    service/       — service implementing *Api interface (internal)
    web/           — controller + DTOs (internal)
    <Module>Api.kt — public interface + public DTOs (the only thing other modules import)

## application.yaml key values
server.port: 8090
api.base-path: /api
jwt.access-token-expiry: 900000 (15 min)
jwt.refresh-token-expiry: 604800000 (7 days)
spring.jpa.hibernate.ddl-auto: validate
spring.flyway.clean-on-validation-error: true (dev only — remove before staging)

## Dependencies (build.gradle.kts)
Spring Boot 4.0.6, Kotlin 2.3.21, Java 25
spring-boot-starter-webmvc, data-jpa, security, validation, flyway, actuator
tools.jackson.module:jackson-module-kotlin (Jackson 3 — group ID changed in Spring Boot 4)
io.jsonwebtoken:jjwt-api:0.13.0
springdoc-openapi-starter-webmvc-ui:3.0.2
spring-modulith-starter-core, spring-modulith-starter-jpa
flyway-database-postgresql
Detekt 2.0.0-alpha.3 (plugin ID: dev.detekt — new ID in 2.0)
Spotless with ktlint

## Permissions seeded in DB
users:read, users:write, users:delete
roles:read, roles:write
leads:read, leads:write, leads:delete
contacts:read, contacts:write, contacts:delete
activities:read, activities:write, activities:delete
quotes:read, quotes:write, quotes:delete
products:read, products:write
reports:view

## Test setup
ModularityTest — verifies Spring Modulith boundaries, runs on every build
Testcontainers with PostgreSQL for integration tests (not H2 — PostgreSQL-specific types used)

