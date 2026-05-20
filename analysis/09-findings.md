# Findings — Audit After 08

> Fresh-session read of `api/src/main/kotlin/**` and `api/src/test/kotlin/**`
> at `claude/audit-synoptic-engine-api-X46UD`. Cross-checked against the four
> ground-truth docs (`00`, `04`, `05` and the CRM bootstrap). 07 / 08 findings
> have shipped and aren't re-litigated.
>
> Grading mirrors 07/08:
>
> - **P0** — security blocker.
> - **P1** — correctness bug or latent leak.
> - **P2** — module-health risk that bites at scale.
> - **P3** — polish.
>
> `AllPermissionsRegisteredTest.kt:29-42` still scans every controller for
> `hasAuthority(…)` keys and asserts each one lives in a
> `PermissionRegistry`; the assertions are intact and the registries cover
> every literal key in the codebase (including the `SharingPermissions.*`
> placeholder-interpolated keys). Nothing to add there.

---

## P0-1 — `/api/mail/inbound-parse` is unauthenticated and writes to the seed tenant

`SecurityConfig.kt:74-75` allow-lists `POST /api/mail/inbound-parse`. The
controller has no `@PreAuthorize` (`EmailController.kt:200-211`) and the
service unconditionally lands every row in `TenantContext.SEED_TENANT_ID`
(`EmailService.kt:288-303`) — the `from`/`to` fields from the request body
never gate the write, and there's no HMAC/secret/IP allow-list. The
endpoint also bypasses `WebFormRateLimiter` (the web-form sibling at
`WebFormController.kt:87-92` does rate-limit). Any anonymous caller can
inject arbitrary `Email` rows into the seed tenant — flooding the inbox,
attaching attacker-controlled HTML bodies to records that admins will open,
and (post-Phase 2 when this dispatches to a real inbox) potentially
spoofing inbound mail from external addresses. The existing
`EmailInboundParseIntegrationTest.kt:10-23` confirms the behaviour as
intended; that's exactly the problem.

**Fix outline.** Either require an inbound-parse webhook secret (HMAC over
the body, header signature check before the controller body runs — same
pattern Stripe/Postmark/SendGrid use), or move the endpoint behind the
authenticated `/api/...` path used by everything else and require a
service-account token. Until that lands, drop the `permitAll()` entry and
require the same JWT every other write needs. The seed-tenant pin in
`parseInbound` should die at the same time — resolve the tenant from the
`to` address.

## P0-2 — `RecordShareService.verifyOwnership` trusts the caller for half the resource types

`RecordShareService.kt:186-208` only verifies the owner tenant for
`leads`, `contacts.persons`, and `contacts.organizations`. For
`products`, `warehouses`, `quotes`, `leads.activities`, and
`products.pricelists` the `else` branch returns `ownerTenantId` — the
function's own input — so the equality check on the next line is always
true. A user in tenant A who knows the UUID of a product in tenant B
can `POST /api/records/share` claiming tenant A owns it; the service
writes a `record_shares` row with `ownerTenantId = A` and a
`resource_visibility` row that grants the consumer tenant access to
tenant B's record. The audit log records the share as originating from
A, and revoking it from A's UI tears down visibility the consumer can
no longer get back from B.

**Fix outline.** Extend `crmApi`/`inventoryApi` with
`findOwnerTenant(resourceType, resourceId)` for every member of
`ResourceType` rather than carrying per-type branches in the sharing
module. The fallback should throw, not trust. The same lookup table is
useful for `CrossTenantWriteInterceptor.resourceTypeFor` (see P1-2).

---

## P1-1 — `ShareMaterializationWorker.matchesFilter` drops every record from filtered policies

`ShareMaterializationWorker.kt:206-209` returns `filterJson == null` — so
policies with a non-null `filter_jsonb` materialize **zero** records, not
"every record" as the docstring at line 203 claims. The
`materializePolicy` loop at line 162-176 logs `"materialized N row(s)"`
where N is the unfiltered count, masking the empty result in operator
logs. Any tenant that ships a filtered share policy (the UI lets them;
`SharePolicyController.kt:37-66` passes `filterJson` straight through)
silently grants visibility to nothing. Combined with the cascade in
`RecordShareService` skipping filter evaluation entirely, the failure
mode is "share works for some policies, mysteriously not for others".

**Fix outline.** Either implement the JSON-DSL evaluator the Sprint 2c
comment promises (operator+attribute+value AST against a row pulled by
id) or — until that lands — reject `filterJson != null` on policy
create/update with a 422 instead of accepting a value the worker will
silently drop. The current accept-then-drop behaviour is worse than
either alternative.

## P1-2 — `CrossTenantWriteInterceptor` audits 4 of 8 resource types

`CrossTenantWriteInterceptor.kt:78-85` maps Lead, Person, Organization,
and Product to a `ResourceType`. Quote, Warehouse, Activity, and Pricelist
fall through the `else -> null` and the interceptor returns without
publishing a `SharedRecordEditedEvent`. The
`CrossTenantAuditEventListener.kt:44-57` then never writes a
`CrossTenantAudit` row for those edits, and the durable audit trail the
sharing model relies on has silent holes — a consumer tenant editing a
shared Quote leaves no record in `cross_tenant_audit`. The four covered
types match the four `verifyOwnership` covers; the missing ones overlap
with P0-2's bypass list, so the same shareable-but-unverified resources
are also unaudited when modified.

**Fix outline.** Replace the four-case `when` with a lookup keyed by the
JPA entity class against the same registry used to resolve owner-tenant
for sharing. Adding an `entity.javaClass.kotlin.findAnnotation<ShareableAs>()`
or a small `Map<KClass<*>, ResourceType>` in the sharing module avoids the
hand-maintained switch and gives the compiler a chance to catch new
shareable entities.

## P1-3 — Inbound-mail callers think send succeeded when it failed

`MailSenderService.kt:34-36` and `:52-54` catch every exception from
`mailSender.send`, log a warning, and return — there is no error
indicator and the method signature returns `Unit`. Every caller
(`AuthService.kt:103-110` password reset, `EmailService.kt:102, 117, 168`
compose / sendDraft / forward, `QuoteService.kt:226-227` quote mail)
treats the call as success: `EmailService.compose` sets
`status = EmailStatus.SENT` before invoking the sender,
`QuoteService.sendMail` returns `SendQuoteMailResponse(sent = true, …)`
regardless of outcome, and `forgotPassword` leaves the reset token in the
DB even when the email containing it never reached the user. The reset
flow is the most dangerous: an SMTP outage gives the user no token and
no error, and the row in `user_password_resets` is still consumable by
anyone who later compromises the table.

**Fix outline.** Let the exception propagate to the transactional caller.
For `forgotPassword`, wrap the token row and the send in the same
transaction so a send failure rolls back the unused token. For
`EmailService.compose` / `QuoteService.sendMail`, surface a failure
status to the caller and persist the row as `FAILED` rather than `SENT`
when the SMTP layer raises. The two `try/catch` blocks in
`MailSenderService` can be deleted outright.

## P1-4 — New cross-module write→write `@Transactional` chain

06 § "Cross-module @Transactional chains (post-Phase 4)" (lines 196-208)
lists every chain known at the audit time and marks them safe. A new one
has landed since:
`TenantProvisioningService.seedTenantDefaults` (identity, `@Transactional`,
`TenantProvisioningService.kt:64-81`) publishes `TenantProvisionedEvent`
which `CrmTenantInitListener.onTenantProvisioned` (crm,
`@Transactional(Propagation.REQUIRED)`,
`CrmTenantInitListener.kt:21-29`) consumes synchronously, then calls
`CrmBootstrapPort.seedDefault{Pipeline,LeadSources,LeadTypes}`
(`CrmBootstrapPortImpl.kt:25-81`, also `@Transactional`). That's a
write→write chain across the identity / crm boundary — exactly the
shape 06's table flags as "fix when extracting either module to its own
TM". The table needs updating, and so does the "Add a note to any future
extract-module PR" guidance at 06:216.

**Fix outline.** Add this chain to the table at `06-implementation-roadmap.md:200-207`
so it's not missed during the inevitable module split. No code change
today — same rationale as the existing entries. When the CRM module
becomes async/multi-process, replace the in-transaction listener with
an outbox-driven `TenantProvisioned` consumer that runs in CRM's own
TM, returning a status event for retry/observability.

---

## P2-1 — `AuthService` uses `jakarta.transaction.Transactional`, bypassing the RLS GUC aspect

`AuthService.kt:11` imports `jakarta.transaction.Transactional`; every
other `@Transactional` in the codebase uses
`org.springframework.transaction.annotation.Transactional`. The pointcut
in `RlsTenantGucAspect.kt:62-64` matches only the Spring annotation, so
`forgotPassword` and `resetPassword` open transactions that never run
the `SET LOCAL app.current_tenant` statement at line 72. Today this is
harmless — both methods run outside a tenant context anyway — but the
divergence creates a trap: anyone adding a third `@Transactional` to
`AuthService` (e.g. an "admin resets another user's password" path
with a tenant context) inherits the missing GUC silently and reads the
wrong rows under RLS in production. Compounding it, the AuthService
methods also miss every other aspect that targets the Spring annotation.

**Fix outline.** Swap the import to
`org.springframework.transaction.annotation.Transactional`. No
behaviour change in the methods themselves, but the aspect ordering at
`Application.kt:12` + `RlsTenantGucAspect.kt:57` now applies. Optional
follow-up: extend the pointcut to match `jakarta.transaction.Transactional`
as well, so future imports of the JTA variant don't silently re-create
the same trap.

## P2-2 — `LeadService.kanban` loads every active lead in a pipeline and filters userId in memory

`LeadService.kt:81-95` calls `leadRepository.findAllByPipelineIdAndDeletedAtIsNull(pipelineId)`
(no pageable, no scope predicate) then applies `leads.filter { it.userId in scopeIds }`
in Kotlin. For a single-pipeline tenant with 50k leads, every kanban
request pulls 50k rows over the wire, allocates them as JPA entities
(with `tags` collections fetched lazily on response mapping → further
N+1), and discards most of them in memory before grouping. The kanban
controller has no upper bound either. The kanban view is also called on
every drag/drop refresh from the UI, so the N runs frequently.

**Fix outline.** Push the scope into the repository — a
`findAllByPipelineIdAndUserIdInAndDeletedAtIsNull(pipelineId, scopeIds)`
variant that the service picks when `scopeIds != null`. For unbounded
pipelines, add a `limit` parameter (e.g. 500 per stage) and a "show
older" pagination affordance; today's blanket fetch is unbounded by
design. The same shape applies to
`ResourceVisibilityService.visibleIds` (`ResourceVisibilityService.kt:43-50`)
which loads every visibility row for a tenant+type before filtering
expirations in memory — push `expires_at` into the query.

## P2-3 — CSV exports load the entire tenant via `PageRequest.of(0, Int.MAX_VALUE)`

`CrmApiImpl.kt:134, 155, 176` and `InventoryApiImpl.kt:55` build a
`PageRequest.of(0, Int.MAX_VALUE)` to pull every Person / Organization /
Lead / Product in the tenant into a list, then map to a CSV row.
`CsvExportService.kt` accumulates the result into a `StringWriter` before
sending. For a tenant with 1M leads, that's 1M hydrated entities + the
serialized CSV string in heap before the first byte goes to the client.
The export controllers (`ImportController.kt:118-133`) don't stream and
don't paginate; the first big tenant to hit them OOMs the JVM.

**Fix outline.** Switch to a streaming response (a Spring
`StreamingResponseBody` or `ResponseBodyEmitter`) that pages through the
repository in chunks (e.g. 1000 at a time) and writes CSV rows to the
servlet output. The repository call needs an ordered key for cursor
pagination so successive pages stay consistent under writes. Same fix
applies to `AttributeService.downloadCsv` (`AttributeService.kt:224-232`)
which does `findAll().forEach` over every attribute row.

---

## P3-1 — Webhook deliveries have no durable audit trail

`WebhookDispatcher.kt:44-56` fires HTTP POSTs for each matched webhook
and writes the outcome only to SLF4J (`log.debug` on success, `log.warn`
on failure). The workflow engine has an analogue
(`WorkflowActionRun` rows persist SUCCESS / FAILED / SKIPPED per action,
`WorkflowEngine.kt:108-119`), and the UI built on top of `workflow_action_runs`
is referenced in 06 § Phase 3. There's no `webhook_delivery_runs` table,
no `/api/settings/webhooks/{id}/deliveries` endpoint, and no retry
queue — a webhook subscriber that's been down for an hour silently
drops every event in that window and the tenant admin has no way to
notice or replay.

**Fix outline.** Mirror the workflow run model: a `webhook_delivery_runs`
table keyed on (webhook_id, event_name, entity_id, attempt_at) with
status / response_code / response_body / error. The dispatcher writes
one row per attempt; a `/api/settings/webhooks/{id}/deliveries` reader
exposes it. For retry, a scheduled drain (same pattern as
`ShareMaterializationWorker`) re-fires `FAILED` rows up to N attempts
with exponential backoff. The tenant audit story is what 06 § Phase 3
already calls for — the table is the missing piece.

## P3-2 — `SystemConfigController` has no integration test

`SystemConfigController.kt` exposes three routes (list, get-by-code,
update) under `settings/config`. Grep over `api/src/test/kotlin` finds no
references to `SystemConfig`, `settings/config`, or any of its DTOs. The
`PermissionDenialCoverageIntegrationTest` doesn't cover these endpoints
either. Unlike the other untested settings sub-modules (`SystemConfig` is
the only one that lacks any integration test), this one writes through
to `system_configs` — a table the bootstrap relies on — and a regression
that drops the update path is invisible in CI.

**Fix outline.** Add a `SystemConfigIntegrationTest` mirroring
`EmailTemplateIntegrationTest` — provision a tenant, exercise list /
get / update with the right auth, assert the round-trip and a 403 from
a viewer token. The test factory already has `tenantProvisioner` and
`TestHttp` helpers, so this is a thin file.

## P3-3 — `CrossTenantAuditController` has no "all my incoming actions" path

`CrossTenantAuditController.kt:32-50` accepts either
(`resourceType` + `resourceId`) or (`actorTenantId` == own tenant);
anything else returns 400. A tenant admin who wants "show me every
cross-tenant edit landed against any of my records this week" has to
already know the resourceIds — there's no
`?ownerTenantId=mine&page=…` form. The service layer
(`CrossTenantAuditService.kt:62-66`) only exposes `byActor` and
`byOwnerResource`; the inverse query (`byOwner` without a specific
resource) isn't on the repository either.

**Fix outline.** Add `findAllByOwnerTenantIdOrderByAtDesc(ownerTenantId,
pageable)` on the repo, a `byOwner` service method, and a third branch
in the controller for `ownerTenantId == principal.tenantId && resourceId
== null`. This is what tenant-admin alerting tooling will hook in
Phase 3 — without it, the audit log is a black box the admin can only
query by guessing record ids.
