# Cross-Company Resource Sharing — Full Analysis

## The Problem We're Solving

> "Large organizations often have a **parent (mother) company** with several **subsidiaries** working on different projects. Managing all their activities, communication, and schedules can become complicated. Or some company needs to share some data with some other company."

Today's options are all bad:
- **Merge tenants** → No data isolation. Everyone sees everything. Not acceptable.
- **Manual export/import** → Stale data, double-entry errors, version conflicts.
- **Third-party integrations** → Expensive, fragile, always behind.
- **Grant full platform access** → Security nightmare. Violates data sovereignty.

We offer a fourth option: **structured, permission-controlled, real-time resource sharing** at the platform level.

---

## Core Concepts

### 1. Tenant Hierarchy

Two tenants can be in one of three relationship types:

| Type | Description | Example |
|------|-------------|---------|
| `PARENT_CHILD` | One tenant owns/controls the other | Holding company → Subsidiary |
| `PARTNER` | Symmetric peer relationship | Two independent companies collaborating |
| `SUPPLIER_CLIENT` | One-directional: supplier pushes catalog/products to client | Manufacturer → Distributor |

A tenant can be in multiple relationships simultaneously (e.g., a subsidiary that is also a partner to a third company).

### 2. Resource Share Policy

A policy is a template that defines what can be shared and how. It is configured by the tenants involved — not by individual users.

**Shareable resource types:**
- `contacts.persons` — Contact persons
- `contacts.organizations` — Organizations/accounts
- `products` — Product catalog with prices
- `leads` — Lead records (for collaborative sales)
- `activities` — Scheduled activities, meetings
- `quotes` — Quote documents

**Access levels (per resource type):**
- `NONE` — Not shared
- `READ` — Can view, cannot modify
- `COMMENT` — Can view and add notes/activities
- `WRITE` — Full CRUD access on shared records
- `MANAGE` — Can reshare to third parties (restricted to admins)

### 3. Individual Resource Shares

Beyond policy-level sharing, individual records can be shared ad-hoc:

- A salesperson can share a specific lead with a partner company ("we're collaborating on this deal")
- A manager can share their product catalog with a specific client tenant

---

## Data Model

### New Tables

```sql
-- Relationship between two tenants
CREATE TABLE tenant_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    target_tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    relationship_type VARCHAR(30) NOT NULL,  -- PARENT_CHILD | PARTNER | SUPPLIER_CLIENT
    status VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending | active | suspended | revoked
    initiated_by UUID REFERENCES users(id),  -- user who created the relationship
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at TIMESTAMPTZ,
    CONSTRAINT uq_tenant_relationship UNIQUE (source_tenant_id, target_tenant_id)
);

-- What resource types are shared between two tenants and at what level
CREATE TABLE tenant_share_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    relationship_id UUID NOT NULL REFERENCES tenant_relationships(id) ON DELETE CASCADE,
    resource_type VARCHAR(50) NOT NULL,  -- contacts.persons | products | leads | etc.
    source_access_level VARCHAR(20) NOT NULL DEFAULT 'NONE',  -- what source tenant grants to target
    target_access_level VARCHAR(20) NOT NULL DEFAULT 'NONE',  -- what target tenant grants to source
    is_bidirectional BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_share_policy UNIQUE (relationship_id, resource_type)
);

-- Individual record shares (ad-hoc, override or extend the policy)
CREATE TABLE resource_shares (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_tenant_id UUID NOT NULL REFERENCES tenants(id),
    target_tenant_id UUID NOT NULL REFERENCES tenants(id),
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID NOT NULL,  -- ID of the shared record in source_tenant
    access_level VARCHAR(20) NOT NULL,  -- READ | COMMENT | WRITE
    shared_by UUID REFERENCES users(id),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

