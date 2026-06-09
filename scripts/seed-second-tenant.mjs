#!/usr/bin/env node
/**
 * Provisions a SECOND tenant ("Helios Robotics") and wires the cross-tenant
 * sharing showcase so the seed tenant's Sharing pages light up:
 *   - Relationships     ← an ACTIVE T1↔T2 PARTNER relationship (+ a share policy)
 *   - Shared with me    ← leads Helios shares INTO the seed tenant
 *   - Cross-tenant Audit← the accept + share actions
 *
 * Handshake: source (T1) creates the relationship (PENDING); target (T2) accepts
 * (→ ACTIVE). Direct record shares don't require the relationship, but we create
 * one so the Relationships page is populated. Idempotent-ish (reuses by slug/pair).
 *
 * Usage: node scripts/seed-second-tenant.mjs   (API on :8090)
 */
const BASE = process.env.API_BASE ?? 'http://localhost:8090/api'
const T1_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@synoptic.dev'
const T1_PASSWORD = process.env.ADMIN_PASSWORD ?? '1234'
const T2 = { name: 'Helios Robotics', slug: 'helios', adminEmail: 'admin@helios.dev', adminPassword: 'Demo@12345' }

async function api(token, method, path, body, silent = false) {
    const res = await fetch(BASE + path, {
        method,
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
        body: body ? JSON.stringify(body) : undefined,
    })
    const t = await res.text()
    let j; try { j = t ? JSON.parse(t) : null } catch { j = t }
    if (!res.ok && !silent) console.error(`  ✗ ${method} ${path} -> ${res.status}`, (typeof j === 'string' ? j : JSON.stringify(j)).slice(0, 180))
    return { ok: res.ok, status: res.status, json: j }
}
const login = async (email, password) => (await api(null, 'POST', '/auth/login', { email, password })).json
const listOf = async (tok, p) => { const r = (await api(tok, 'GET', p)).json; return Array.isArray(r) ? r : (r?.content ?? []) }

async function main() {
    // ── T1 (seed tenant) ──
    const t1 = await login(T1_EMAIL, T1_PASSWORD)
    if (!t1?.accessToken) throw new Error('T1 login failed')
    const T1_ID = t1.tenantId
    console.log('✓ logged in as T1 (seed tenant)', T1_ID)

    // ── Provision / find T2 ──
    let tenant2 = (await listOf(t1.accessToken, '/tenants')).find((t) => t.slug === T2.slug)
    if (!tenant2) {
        const r = await api(t1.accessToken, 'POST', '/tenants', T2)
        if (!r.ok) throw new Error('tenant provisioning failed')
        tenant2 = r.json
        console.log('✓ provisioned tenant', T2.name)
    } else {
        console.log('• tenant', T2.name, 'already exists — reusing')
    }
    const T2_ID = tenant2.id

    // ── T2 admin ──
    const t2 = await login(T2.adminEmail, T2.adminPassword)
    if (!t2?.accessToken) throw new Error('T2 login failed')
    console.log('✓ logged in as T2 (Helios)', t2.tenantId)

    // ── T2 creates a few leads to share ──
    const p2 = (await api(t2.accessToken, 'GET', '/pipelines')).json[0]
    const t2Leads = []
    for (const [title, amount, st] of [
        ['Helios — Joint robotics integration', 88000, 2],
        ['Helios — Co-sell: warehouse automation', 142000, 3],
        ['Helios — Reseller agreement', 36000, 1],
    ]) {
        const existing = (await listOf(t2.accessToken, '/leads?size=200')).find((l) => l.title === title)
        const r = existing ? { ok: true, json: existing } : await api(t2.accessToken, 'POST', '/leads', {
            title, amount, pipelineId: p2.id, stageId: p2.stages[st].id,
        })
        if (r.ok) t2Leads.push(r.json)
    }
    console.log(`✓ T2 has ${t2Leads.length} leads to share`)

    // ── Relationship T1 → T2 (PARTNER), accepted by T2 ──
    let rel = (await listOf(t1.accessToken, '/relationships')).find(
        (r) => r.targetTenantId === T2_ID && r.sourceTenantId === T1_ID
    )
    if (!rel) {
        const r = await api(t1.accessToken, 'POST', '/relationships', {
            targetTenantId: T2_ID, type: 'PARTNER', note: 'Channel partner — joint robotics + warehouse automation deals.',
        })
        if (r.ok) rel = r.json
    }
    if (rel && rel.status !== 'ACTIVE') {
        // T2 is the target → T2 accepts.
        await api(t2.accessToken, 'PATCH', `/relationships/${rel.id}/accept`)
        console.log('✓ relationship accepted by T2 → ACTIVE')
    } else if (rel) {
        console.log('• relationship already ACTIVE')
    }

    // ── Policy on the relationship (created by the source, T1) ──
    if (rel) {
        const policies = await listOf(t1.accessToken, `/relationships/${rel.id}/policies`)
        if (!policies.some((p) => p.resourceType === 'leads')) {
            await api(t1.accessToken, 'POST', `/relationships/${rel.id}/policies`, {
                resourceType: 'leads', accessLevel: 'READ', materialize: false,
            }, true)
        }
        console.log('✓ share policy on relationship (leads · READ)')
    }

    // ── Helios (T2) shares leads INTO the seed tenant (T1) → "Shared with me" ──
    let intoT1 = 0
    for (const lead of t2Leads.slice(0, 2)) {
        const r = await api(t2.accessToken, 'POST', '/records/share', {
            consumerTenantId: T1_ID, resourceType: 'leads', resourceId: lead.id, accessLevel: 'READ',
            note: 'Shared for joint pursuit.',
        })
        if (r.ok) intoT1++
    }
    console.log(`✓ Helios shared ${intoT1} leads into the seed tenant`)

    // ── Seed tenant (T1) shares leads OUT to Helios (T2) → outgoing + audit ──
    const t1Leads = await listOf(t1.accessToken, '/leads?size=200')
    let outToT2 = 0
    for (const lead of t1Leads.filter((l) => /Edge Gateway|analytics deployment/i.test(l.title)).slice(0, 2)) {
        const r = await api(t1.accessToken, 'POST', '/records/share', {
            consumerTenantId: T2_ID, resourceType: 'leads', resourceId: lead.id, accessLevel: 'READ',
            note: 'Shared with channel partner.',
        })
        if (r.ok) outToT2++
    }
    console.log(`✓ seed tenant shared ${outToT2} leads out to Helios`)

    // ── Summary from T1's perspective ──
    const sharedWithMe = await listOf(t1.accessToken, '/records/shared-with-me')
    const rels = await listOf(t1.accessToken, '/relationships')
    console.log('\n=== seed-tenant sharing now shows ===')
    console.log(`  relationships: ${rels.length}  (${rels.map((r) => r.status).join(', ')})`)
    console.log(`  shared-with-me: ${sharedWithMe.length} record(s)`)
    console.log(`  Helios admin login: ${T2.adminEmail} / ${T2.adminPassword}`)
}
main().catch((e) => { console.error('FATAL', e); process.exit(1) })
