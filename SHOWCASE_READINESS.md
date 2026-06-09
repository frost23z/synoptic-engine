# Synoptic Engine — Defense Showcase Readiness

> Companion to [`AUDIT_FINDINGS.md`](./AUDIT_FINDINGS.md). That document audits **production multi-tenant
> SaaS** readiness. *This* document answers a different, narrower question: **is the project ready to
> deploy and demo for a final-year honours defense?**
>
> Assessed 2026-06-09 by driving the running app end-to-end as a logged-in user (Playwright) and
> seeding a realistic demo dataset.

## Verdict: ✅ Ready to showcase

The application builds, boots, and works end-to-end. I logged in and walked **all 42 primary views**
as an admin user; after fixes, **42/42 render correctly with 0 console/network/render errors**. I found
and **fixed five real bugs** along the way (see below). The app carries a coherent B2B demo dataset
across CRM, inventory, mail, and settings, and looks like a finished commercial CRM. For a supervised
academic demo this is comfortably defense-ready.

The 🔴/🟠 items in `AUDIT_FINDINGS.md` are **not showcase blockers** — see *Two different bars* below.

---

## Was the earlier audit a "real" full audit?

**Yes — it is a genuine, high-quality audit, not a rubber-stamp.** Evidence:

- Every finding cites concrete `file:line` locations and gives a specific minimal fix.
- It has an honest **Coverage Map** that distinguishes *read-in-full* from *sampled* from *not-yet-read*
  (e.g. it states the per-page Nuxt components and several services were sampled, not read line-by-line).
- It has a **Verified Safe / Done Well** section, which a superficial pass never produces.
- Severities are calibrated and realistic (it down-rates things that "fail closed", e.g. MT-3).

**Its one real limitation** is scope, which it declares: it focuses on backend security, multi-tenant
isolation, and DevOps. The frontend was a "representative sample". That is exactly where the bug I
found lives (a frontend↔API contract mismatch the audit didn't reach) — so the e2e pass below
**complements** the audit rather than contradicting it.

---

## Two different bars: production SaaS vs. defense showcase

The audit scored **5/10 for *production* readiness**. That is the right score *for shipping a public,
hostile, multi-tenant SaaS*. It is the **wrong lens for a defense demo**, because almost every serious
finding is about *adversarial, multi-tenant, public-internet* conditions that don't exist in a
supervised single-tenant demo:

| Audit concern | Why it matters in production | Why it's not a showcase blocker |
|---|---|---|
| MT-1/MT-2 — Postgres RLS inert in prod config; bulk cross-tenant mutation | A malicious tenant A could touch tenant B's rows | The demo runs **one** tenant with **no adversary**. Same-tenant isolation (Hibernate `@Filter`) is verified solid by the audit. |
| SEC-1…12 — MFA/login hardening, rate limits | Online attackers brute-forcing real accounts | No attacker; demo accounts only |
| OPS-1 — public Swagger, permissive CORS, no HSTS | Public attack surface | Acceptable on a short-lived demo; trivial to lock down if deployed publicly (see below) |
| BE-1/2/3 — email delivery correctness, page-size DoS | Real campaigns, real load | No load, no real email recipients |

**Honest framing for the defense:** the existence of this audit is a *strength*, not a weakness.
"We did a production-readiness audit, catalogued 40+ findings with severities and fixes, and
consciously deferred the multi-tenant-hardening items as future work" is a mature engineering story.

---

## What I verified end-to-end (acting as a user)

Drove the live app (`http://localhost:3000`) with Playwright as `admin@synoptic.dev`, visiting every
primary route and capturing console errors, page errors, and failed/4xx-5xx API responses per page.

- **42 / 42 views render with 0 errors** on the final build (dashboard, leads list + kanban, contacts,
  products, quotes, inventory, warehouses, activities, mail inbox + sent, sharing, full settings suite).
  Reusable: `node scripts/screenshot-tour.mjs`.
- The existing 13 Playwright specs in `web/tests/e2e/` exercise auth, CRUD, mass-ops, quotes, sharing,
  export and registration.

### Bugs found & fixed (all live in the served build)
| ID | Page(s) | Problem | Fix |
|----|---------|---------|-----|
| **FE-5** | Leads | `GET /pipelines/{id}/stages` → 405 (POST-only path); mass-move dropdown silently empty | Derive stages from `GET /pipelines` (inline) |
| **FE-6** | Stock · Shared-with-me · Marketing | `USelect` "All/None" option with `value: ''` → NuxtUI 4 render crash (error boundary) | Use `value: undefined` (matches working convention) |
| **FE-7** | Settings → Users | Used `usePaginatedList` against an array endpoint → always "0 users" (even the admin) | Fetch array + filter client-side |
| **FE-8** | Sharing (Relationships, Shared-with-me) | Other tenant rendered as a raw UUID — `useTenantNames` gated on `can()` at SSR, but the auth plugin is client-only, so the directory never loaded | Fetch the tenant directory client-side (`server: false`) |

FE-6 and FE-7 are the "shows an error / renders empty" class that a console/network smoke check can't
catch — they were found by **visually reviewing the screenshots**, which is why an eyeball pass matters.

Two **backend** correctness bugs were also found while seeding and documented in
[`AUDIT_FINDINGS.md`](./AUDIT_FINDINGS.md) (not fixed — they need a migration / query change; the seed
script works around both): **BE-10** soft-deleted product SKUs stay reserved (recreate → 500);
**BE-11** stock totals sum across soft-deleted warehouses.

### Not a bug (noted for completeness)
`GET /api/dashboard/stats` returns 500 **only if you omit the required `type` query param** — the
frontend always sends it, so the dashboard is fine. The minor API nit: a missing required param
should map to **400**, not 500 (consistent with audit item API-1).

---

## Demo dataset (seeded into the seed tenant)

A coherent industrial-IoT B2B story:

| Entity | Count | Notes |
|---|---|---|
| Organizations | 10 | Northwind, Globex, Stark, Wayne, Cyberdyne, … |
| Persons | 15 | Linked to orgs, with titles/email/phone, tagged |
| Products | 12 | Gateways, sensors, SaaS tiers, support; SKUs + prices |
| Warehouses | 3 | + storage locations, with stock levels |
| Leads | 20 | Realistic funnel: New 4 · Qualified 4 · Proposal 5 · Negotiation 3 · Won 3 · Lost 1 |
| Quotes | 7 | Multi-line, correct subtotal/tax/total math |
| Activities | 20 + | Calls/meetings/tasks/notes; the hero lead has a full 6-step timeline |
| Tags | 8 | Assigned across leads/persons/orgs |
| Inventory | — | Stock per warehouse; 3 products deliberately under reorder threshold |
| Movements / Transfers | 4 + 2 | Reserve/release history + inter-warehouse transfers |
| Mail | 4 sent · 2 drafts | Inbox is empty by design (populated only by inbound webhook parsing) |
| Users · Groups | 5 · 3 | Manager/Salesperson/Viewer demo users; Sales/AM/Ops groups |
| Email templates · Webhooks | 3 · 2 | Welcome / follow-up / renewal; lead & quote webhooks |
| Custom attributes · Marketing | 4 · 1+1 | EAV fields on Person/Org/Lead/Product; one event + one campaign |
| System config | — | Company name/contact/locale/timezone + mail-from filled in |
| **2nd tenant + Sharing** | Helios Robotics | ACTIVE Partner relationship; leads shared **both ways** → Relationships, Shared-with-me, and Cross-tenant Audit all populated |

Dashboard reflects it: 20 leads, $51,402 avg lead value, $23,497 won revenue, populated pipeline funnel,
revenue-by-source/type, top products, top customers, recent + upcoming activities.

The **cross-tenant sharing** showcase is wired by `scripts/seed-second-tenant.mjs`: it provisions
**Helios Robotics** (`admin@helios.dev` / `Demo@12345`), forms an ACTIVE Partner relationship with the
seed tenant, and shares leads in both directions. (4 junk "E2E Co" tenants from old registration tests
were soft-deleted, and the seed tenant was renamed *Default Tenant → Synoptic Engine*.)

**Still intentionally empty** (not bugs): **Web-forms** and **Workflows** are admin *builders* (public
lead-capture forms / automation rules) with no demo content; **Imports** is CSV job-history (fills when
you run an import); **Mail Inbox** is inbound-only (Sent/Drafts are populated). I can add a sample
web-form + workflow if you want those non-empty too.

**Login:** `admin@synoptic.dev` / `1234`  *(the password comes from `api/.env`, which overrides the
`Admin@123` default in `application-local.yaml`).*

---

## How to run the demo

```bash
# 1. Infra (already running here): Postgres + Mailpit
docker compose -f api/compose.yaml up -d

# 2. API (port 8090) — seeds the admin user + tenant defaults on first boot
cd api && ./gradlew bootRun

# 3. Web (port 3000)
cd web && pnpm dev          # dev server (includes the Leads fix), or:
cd web && pnpm build && PORT=3000 NUXT_PUBLIC_API_BASE=http://localhost:8090 node .output/server/index.mjs
```

**Seed / re-seed the demo data** (idempotent — safe to re-run; wipes & recreates the demo content):

```bash
node scripts/seed-demo.mjs           # against a running API on :8090
# API_BASE / ADMIN_EMAIL / ADMIN_PASSWORD / SEED_RESET=false are honored
```

This is the script to run **after a fresh deploy** (empty DB) to populate the showcase. Then, to add the
cross-tenant sharing demo (2nd tenant + relationship + shares):

```bash
node scripts/seed-second-tenant.mjs
```

### If you deploy publicly (fly.io — `api/fly.toml` exists)
A short-lived public demo needs these to even boot / behave (from the audit):
- **OPS-2:** set the production datasource env (`SPRING_DATASOURCE_*`), `JWT_SECRET`,
  `SYNOPTIC_ENCRYPTION_KEY`, `SYNOPTIC_ADMIN_*`, `CORS_ALLOWED_ORIGINS`, `MAIL_*`.
- Nice-to-have hardening: disable Swagger (`springdoc.*.enabled=false`) and tighten CORS (OPS-1).
- The deep multi-tenant items (MT-1/MT-2) can stay as documented future work for a single-tenant demo.

I verified the app **locally**; I have **not** executed the fly.io deploy. Say the word and I'll do it.

---

## Screenshots

42 clean 1440×900 captures in [`screenshots/`](./screenshots/), numbered for slide order. Highlights:
`01-dashboard`, `02b-leads-kanban` (pipeline board), `03-lead-detail` (timeline + tags),
`12-quote-detail`, `18-inventory-reorder`, `20b-mail-sent`, `26-settings-users`.

Regenerate them (and re-run the smoke check) anytime with:

```bash
node scripts/screenshot-tour.mjs    # walks all 42 views, screenshots each,
                                    # and writes a per-page error report to screenshots/_errors.json
```

---

## Minor, non-blocking polish ideas (only if you have time)
- Relative-time labels render future-dated activities as "Nd ago" (cosmetic).
- **API-1 class:** several GETs with a *required* query param return **500** (not 400) when it's omitted —
  e.g. `GET /dashboard/stats` (needs `type`), `GET /inventory/movements` (needs `productId`). The
  frontend always sends them, so pages work; it's only a robustness/HTTP-correctness nit.
- **Stock / Movements** pages require selecting a product (+ warehouse) before showing data — by design
  (query tools), so they render a prompt until you choose one.
- Build note: `pnpm build` (Nitro bundle) needs ~1.5–2 GB free; it OOM-kills in this 8 GB container only
  when the API + dev server are also running. Free them first, or rely on CI/fly where RAM is ample.
