# FUTURE WORK — Agentic CRM (post-MVP) — Synoptic Engine

> ⚠️ **NOT part of the MVP.** The MVP is Krayin parity + enterprise-grade
> multi-tenant backend (✅ complete) + a workable frontend (in progress — see
> `FRONTEND_PLAN.md`). This document is the **future product direction**: making
> Synoptic Engine AI-functional / agentic in the spirit of Octolane
> ("the best CRM is no CRM"). Do not start this work until the MVP frontend ships.
>
> Authored: 2026-06-02 · Kept as the plan-of-record for the agentic phase.
> Companion docs: `FRONTEND_PLAN.md` (current work), `api/CLAUDE.md` (arch rules).
>
> Part 1 below (backend-readiness verdict) is retained because it documents *why*
> the backend is a strong substrate for agents; its current/actionable items
> (run the full test suite, doc hygiene) are tracked in the current-phase docs.

---

## Part 1 — Backend Readiness Verdict

**Verdict: READY for the frontend phase.** The backend is a mature, well-architected
Spring Boot 4 modular monolith. It compiles clean and the architecture is unusually
well-suited to hosting AI agents.

### Evidence
- `./gradlew testClasses` → **BUILD SUCCESSFUL** (full main + test compile). Only two
  trivial Kotlin warnings (an unnecessary `?.` in `UserService.kt:124`, an unnecessary
  `!!` in `CrossTenantAuditIntegrationTest.kt:192`).
- Scale: **33 controllers, 47 services, 59 entities, 26 Flyway migrations (next: V027),
  ~62 integration tests across 95 test files, ~27k Kotlin LOC.**
- Krayin parity ≈ 100% (per `BACKEND_PLAN.md`), plus enterprise features (MFA, API keys,
  login history, audit, password policy) beyond Krayin scope.
- The full Testcontainers integration suite was **not executed here** (no Docker daemon in
  this environment). Verdict rests on a clean compile + 62 existing integration tests +
  the documented test-hardening history (Phases 5 & 7). **Action: run `./gradlew test`
  on a Docker-capable machine as the final gate before frontend.**

### What is genuinely production-grade (and why it matters for AI)
| Subsystem | State | Why it matters for agents |
|---|---|---|
| Domain events + `WorkflowEngine` (`@EventListener @Async`, 10 actions, condition eval, `workflow_action_runs` audit) | ⭐⭐⭐⭐⭐ | The natural place to fire autonomous agent loops on business events. |
| Webhooks (`WebhookDispatcher`, HMAC-SHA256 signing, SSRF guard, delivery audit) | ⭐⭐⭐⭐⭐ | Outbound action channel; agent-to-external integration ready. |
| API-key auth (`sk_` prefix, SHA-256 stored, per-key `lastUsedAt`, expiry) | ⭐⭐⭐⭐⭐ | Service-to-service auth for a non-human agent identity. |
| Multi-tenancy (RLS **+** Hibernate `@Filter`, `TenantContext`/`ActorContext` propagated to async via `TenantPropagatingTaskDecorator`) | ⭐⭐⭐⭐⭐ | An agent acting "as a tenant" inherits bullet-proof isolation, even on background threads. |
| EAV custom attributes (`Attribute`/`AttributeValue`, 13 types, runtime-defined) | ⭐⭐⭐⭐⭐ | AI-generated fields (score, enrichment, segment) need **no migration**. |
| Async + 6 scheduled workers (marketing send w/ retry+backoff, reorder, retention, cleanup) | ⭐⭐⭐⭐ | Templates for autonomous agent jobs; tenant-aware. |
| Activities / Email / Quotes | ⭐⭐⭐⭐ | The "things an agent acts on" — all relational, auditable, workflow-wired. |
| OpenAPI/Swagger (`/v3/api-docs`, `/swagger-ui`) | ⭐⭐⭐⭐ | Frontend can generate a typed client; agents get a machine-readable tool catalog. |
| Existing AI (`AiLeadService` → Claude Opus 4.8, prompt caching, adaptive thinking, Tika) | ⭐⭐⭐ | Proves the integration works — but it's a one-off (see gaps). |

### Frontend-prep hygiene (now tracked in the current phase)
The doc/tooling gaps originally noted here — missing `GUIDELINES.md`, stale `api/README.md`,
no generated OpenAPI client, thin component library — are **current-phase** concerns, not
agentic ones. They are now addressed/tracked in **`FRONTEND_PLAN.md`** (and the stale READMEs
have been rewritten). Nothing here blocks the frontend work.

---

## Part 2 — What "AI-native like Octolane" Actually Means

Octolane's thesis: **"The best CRM is no CRM."** The product does the sales admin *for*
the user. Decoded into capabilities:

| Octolane capability | Underlying requirement |
|---|---|
| Auto-drafts follow-up emails (≈3,200/mo) | LLM drafting from thread/lead context + a review/send surface. |
| Auto-updates deal fields from conversations | **Continuous data capture** (email/calendar/meeting transcripts) + extraction. |
| Research agents prep for calls (company enrichment) | Enrichment pipeline (web/public data) + storage on the record. |
| Real-time CRM updates during demos | Meeting-recorder/transcript ingestion → field extraction. |
| Email sequences + follow-up reminders | Multi-step scheduled, personalized sending. |
| Visitor tracking | Web tracking script + intent ingestion. |
| "5 tools in 1" | CRM + recorder + sequencer + reminders + tracking, unified. |

**Honest read of the moat:** Octolane's advantage is **(a) continuous data ingestion**
(email/calendar/meeting/web) and **(b) the autonomy + human-in-the-loop UX** — *not* the
LLM itself. The model call is the easy 10%. The hard 90% is feeding the agent good data
and earning enough trust to let it act.

---

## Part 3 — How AI-Ready Is Synoptic *Today*? (Honest Feedback)

**The bones are excellent — better positioned than most CRMs to become agent-native.**
Events, workflow-actions, webhooks, EAV, async, API-keys, and tenant-safe context
propagation are *exactly* the substrate an agent platform needs, and they already exist.

**But reaching Octolane-style autonomy is mostly NEW product surface, not a refactor:**

1. **No reusable LLM layer.** `AiLeadService` hardcodes the model and is synchronous, with
   no retries, no tool-use, no token/cost accounting, and it discards the thinking output.
   It even creates leads with `person/org/pipeline = null` (extracts only 4 fields). It's a
   proof-of-concept, not a platform.
2. **No agent orchestration.** There is no tool-use loop, no agent run/trace model, no
   notion of an "AI actor" identity, no autonomy policy, no human-in-the-loop approval.
3. **No continuous data ingestion — the real prerequisite.** The autonomous magic depends
   on always-on capture of emails, calendar, and meeting transcripts. Today the only AI
   input is a manually uploaded file. **This is the single biggest gap, and it's integration
   work (Gmail/Outlook/calendar/recorder), not prompt engineering.**
4. **No semantic memory.** No embeddings/vector store, so the agent can't "remember" or do
   natural-language retrieval over CRM history.
5. **No cost/guardrail framework.** Autonomy without budget caps + approval gates is a
   business risk.

**Recommendation:** Do **not** try to clone Octolane feature-for-feature. Ship the 2–3
highest-ROI, lowest-trust-barrier agent features first (enrich, score, draft-follow-up —
all human-reviewed), prove value, then layer autonomy and ingestion. Lean into your real
differentiator that Octolane lacks: **cross-company resource sharing + ERP depth** — an
agent that can (with permission) enrich and match across the shared tenant network is a
genuinely novel moat.

---

## Part 4 — The Plan (phased, agent-executable)

Each phase is a set of small, independently shippable tasks. Every task mirrors an existing
backend pattern (cited), pairs any migration with its entity in the same commit, carries
`@PreAuthorize` + `@Valid`, and lands with an integration test. This makes them safe for an
AI coding agent to execute one PR at a time.

### Phase 0 — AI Platform Foundation (backend, ~1–2 weeks)
*Goal: a reusable, observable, governed AI substrate. No user-facing features yet.*

- **T0.1 — `LlmGateway` abstraction.** Extract the Anthropic call out of `AiLeadService`
  into `shared/ai/LlmGateway` (interface + Anthropic impl). Responsibilities: model
  selection, prompt caching, adaptive thinking, **tool-use / function-calling**, timeouts,
  retries w/ backoff (mirror `MarketingSendWorker`), and structured results (text + tool
  calls + token usage). Refactor `AiLeadService` to use it (and fix it to extract+link
  person/org while you're there).
- **T0.2 — AI run tracing.** Migration **V027** `ai_runs` table (mirror
  `workflow_action_runs`): tenant_id, actor_id, feature, model, prompt_tokens,
  completion_tokens, cost, latency_ms, status, related_entity_type/id, error. Persist every
  `LlmGateway` call.
- **T0.3 — AI config per tenant.** Use the existing `system_configs` catalog: `ai.enabled`,
  `ai.model`, `ai.monthly_budget_usd`, `ai.autonomy_level` (`off` | `suggest` |
  `approve` | `auto`). Add a budget guard backed by the `RateLimiter` interface.
- **T0.4 — AI actor identity.** Seed a per-tenant system "AI Agent" user (or dedicated
  `sk_` API key) so agent-authored rows get correct `createdBy` via `ActorContext` and are
  RLS-scoped. Document `ANTHROPIC_API_KEY` + new `synoptic.ai.*` keys in `application.yaml`.
- **T0.5 — `run_ai_task` workflow action.** New `WorkflowAction` in
  `shared/automation/actions/` so any workflow event can invoke an AI task and write the
  result to an EAV field. (This is the bridge between the existing automation engine and AI.)

### Phase 1 — AI Assist, human-in-the-loop (highest ROI, ~2–4 weeks)
*Goal: visible value, low trust barrier. Everything is suggested or drafted, never sent
autonomously. This is the Octolane core, minus autonomy.*

- **T1.1 — Lead/contact enrichment.** `EnrichmentService.enrich(entity)` → derives firmographics
  from available data, writes `ai_*` EAV attributes. Endpoint + `run_ai_task` action.
- **T1.2 — Lead scoring.** `ai_lead_score` (0–100) EAV field, recomputed on
  `lead.created`/`lead.updated` via an `@Async` listener (mirror `WorkflowEngine`).
- **T1.3 — Email draft generation.** `POST /api/leads/{id}/draft-email` → creates a **DRAFT**
  `Email` (human reviews & sends). This is Octolane's #1 feature. Reuse `EmailTemplate` vars.
- **T1.4 — Timeline summarization.** `GET /api/leads/{id}/ai-summary` — summarize the
  activity/email timeline.
- **T1.5 — Next-best-action.** Suggest 1–3 concrete next actions per lead.

### Phase 2 — Autonomous Agent Loop (with guardrails, ~3–5 weeks)
*Goal: the agent decides and acts, but within an approval/budget cage.*

- **T2.1 — Agent orchestrator** (`shared/ai/agent/`): a bounded tool-use loop. Tools are thin
  wrappers over existing services: `createActivity`, `draftEmail`, `updateLead`, `addTag`,
  `createQuote`, `enrich`, `score`, `search`. Cap iterations; log each step to `ai_runs`.
- **T2.2 — Event-triggered agent.** On `lead.created`: enrich → score → assign → draft
  welcome (proposed, not sent). Gated by `ai.autonomy_level`.
- **T2.3 — Scheduled "work the pipeline" job** (mirror a `@Scheduled` worker): find stale /
  rotten leads daily, draft follow-ups, surface to the user.
- **T2.4 — Human-in-the-loop approval ("Agent Inbox").** Proposed actions table + endpoints
  to approve/edit/reject. **Never auto-send external email until autonomy = `auto` AND an
  explicit per-tenant opt-in.** Full audit via `ai_runs` + existing `audit_log`.

### Phase 3 — Octolane Parity / Differentiators (ongoing)
- **T3.1 — Conversation/meeting ingestion.** `POST /api/ingest/transcript` → extract deal
  updates + create follow-ups (Octolane's "real-time updates during demos"). *Prereq for
  true autonomy.*
- **T3.2 — Email/calendar sync** (Gmail/Outlook). The biggest integration lift; the real
  unlock for "no data entry." Feeds T2/T3.
- **T3.3 — Email sequences.** Multi-step, AI-personalized, on the marketing send worker.
- **T3.4 — Semantic memory (pgvector).** Embeddings over activities/emails/leads →
  retrieval for agent context + "ask your CRM" natural-language query.
- **T3.5 — Cross-network enrichment (your moat).** With sharing-policy permission, let the
  agent enrich/match across the cross-tenant network. Octolane can't do this.
- **T3.6 — Visitor tracking** (optional): web script + intent ingestion.

### Frontend AI surfaces (Nuxt, parallel with Phase 1+)
- Command bar (Cmd-K) → agent; **streaming via SSE** from the backend.
- Per-lead **AI panel**: score, summary, suggested actions, "Draft email" button.
- **Agent Inbox** page: approve / edit / reject proposed actions.
- Settings → AI: model, autonomy level, monthly budget, feature toggles.

---

## Part 5 — Executing "everything with AI agents"

This plan is structured so an AI coding agent can run it. Working agreement:

1. **One capability per PR.** Small, reviewable, independently revertible.
2. **Mirror the cited pattern.** Each task names the existing file to copy
   (`MarketingSendWorker` for retry, `WorkflowEngine` for listeners, `workflow_action_runs`
   for run tables, a `*PermissionRegistry` for new permissions).
3. **Definition of done:** compiles (`./gradlew testClasses`), an integration test added
   (`AbstractIntegrationTest` + Testcontainers), migration paired with entity in the same
   commit, every endpoint has `@PreAuthorize` + `@Valid`, OpenAPI updated.
4. **Architecture decisions stay human.** Autonomy model, guardrails, data-ingestion
   choices, and budget policy are designed by a human; agents implement within those rails.
5. **Guardrails are non-negotiable.** Budget caps (T0.3) and approval gates (T2.4) land
   *before* any autonomous send.

## Part 6 — Risks & Honest Caveats
- **Scope.** Phases 0–2 are months of focused work, not a weekend. Octolane is a funded
  team shipping daily; match ambition to runway.
- **Data, not models, is the bottleneck.** Without email/calendar/meeting ingestion (T3.1/
  T3.2), the agent has little to act on. Prioritize one ingestion source early if autonomy
  is the goal.
- **Trust & liability.** An agent that emails customers autonomously is a brand risk. Default
  to suggest/approve; make `auto` an explicit, audited opt-in.
- **Cost.** Opus-per-event across a busy pipeline adds up. Use cheaper models (Haiku) for
  scoring/classification, reserve Opus for drafting/reasoning; enforce per-tenant budgets.
- **Don't let agents architect.** "Everything by AI agents" works for well-scoped tasks that
  mirror existing patterns; it does not replace the human design decisions above.

## Recommended first step
Phase 0 (T0.1–T0.4) — the LLM gateway, run tracing, config/budget, and AI actor identity.
It unblocks everything else and is low-risk, pure-backend, fully testable. Then ship T1.3
(draft-follow-up email) as the first user-visible win — it's the feature Octolane leads with.
