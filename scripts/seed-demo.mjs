#!/usr/bin/env node
/**
 * Synoptic Engine — demo data seeder for the defense showcase.
 *
 * Wipes the seed tenant's CRM + inventory data and re-creates a coherent
 * industrial-IoT B2B dataset: organizations, contacts, products, warehouses +
 * stock (with a realistic reorder list), a 20-lead pipeline funnel, quotes,
 * tagged records, and a "hero" lead with a full activity timeline.
 *
 * Usage:
 *   node scripts/seed-demo.mjs
 * Env (all optional):
 *   API_BASE   default http://localhost:8090/api
 *   ADMIN_EMAIL / ADMIN_PASSWORD   default admin@synoptic.dev / 1234
 *   SEED_RESET=false   to skip the wipe and only add on top of existing data
 */
const BASE = process.env.API_BASE ?? 'http://localhost:8090/api'
const EMAIL = process.env.ADMIN_EMAIL ?? 'admin@synoptic.dev'
const PASSWORD = process.env.ADMIN_PASSWORD ?? '1234'
const RESET = process.env.SEED_RESET !== 'false'

let TOKEN = ''
async function api(method, path, body, silent = false) {
    const res = await fetch(BASE + path, {
        method,
        headers: { 'Content-Type': 'application/json', ...(TOKEN ? { Authorization: 'Bearer ' + TOKEN } : {}) },
        body: body ? JSON.stringify(body) : undefined,
    })
    const t = await res.text()
    let j; try { j = t ? JSON.parse(t) : null } catch { j = t }
    if (!res.ok && !silent)
        console.error(`  ✗ ${method} ${path} -> ${res.status}`, (typeof j === 'string' ? j : JSON.stringify(j)).slice(0, 160))
    return { ok: res.ok, status: res.status, json: j }
}
const pick = (a, i) => a[((i % a.length) + a.length) % a.length]
const day = (d) => { const t = new Date(); t.setDate(t.getDate() + d); return t }
const dstr = (d) => d.toISOString().slice(0, 10)
const iso = (d) => d.toISOString()

// ─────────────────────────────────────────────────────────── data ──────────
const ORGS = [
    ['Northwind Traders', '120 Market St, Seattle, WA', '+1-206-555-0118'],
    ['Globex Corporation', '500 Industrial Pkwy, Austin, TX', '+1-512-555-0142'],
    ['Initech LLC', '4120 Freidrich Ln, Austin, TX', '+1-512-555-0199'],
    ['Stark Manufacturing', '10880 Malibu Point, Malibu, CA', '+1-310-555-0177'],
    ['Wayne Enterprises', '1007 Mountain Dr, Gotham, NJ', '+1-201-555-0123'],
    ['Soylent Foods', '88 Greene St, New York, NY', '+1-212-555-0166'],
    ['Hooli Inc', '1401 N Shoreline Blvd, Mountain View, CA', '+1-650-555-0133'],
    ['Vandelay Industries', '129 W 81st St, New York, NY', '+1-212-555-0188'],
    ['Acme Logistics', '742 Evergreen Terrace, Springfield, IL', '+1-217-555-0150'],
    ['Cyberdyne Systems', '18144 El Camino Real, Sunnyvale, CA', '+1-408-555-0145'],
]
const PEOPLE = [
    ['Diane', 'Nguyen', 'VP of Operations'], ['Marcus', 'Bennett', 'Chief Technology Officer'],
    ['Priya', 'Sharma', 'Procurement Manager'], ['Tom', 'Okafor', 'Director of IT'],
    ['Elena', 'Rossi', 'Head of Engineering'], ['James', 'Carter', 'Plant Manager'],
    ['Sofia', 'Martinez', 'VP of Sales'], ['Liam', 'OBrien', 'Operations Analyst'],
    ['Hannah', 'Cohen', 'Facilities Director'], ['Raj', 'Patel', 'Logistics Lead'],
    ['Grace', 'Kim', 'CEO'], ['David', 'Mueller', 'Maintenance Supervisor'],
    ['Aisha', 'Khan', 'Supply Chain Manager'], ['Peter', 'Larsen', 'CFO'],
    ['Nora', 'Adeyemi', 'Innovation Lead'],
]
const PRODUCTS = [
    ['Synoptic Edge Gateway X1', 'SE-GW-X1', 1299, 25, 'hw', 'Industrial edge gateway with 4G/Wi-Fi failover and on-device analytics.'],
    ['Synoptic Sensor Node S200', 'SE-SN-S200', 149, 100, 'hw', 'Multi-channel environmental sensor node (temp, humidity, vibration).'],
    ['Synoptic Power Meter P50', 'SE-PM-P50', 329, 40, 'hw', 'Three-phase power monitoring meter with Modbus output.'],
    ['Synoptic Vibration Probe V10', 'SE-VP-V10', 89.5, 60, 'hw', 'High-frequency vibration probe for rotating machinery.'],
    ['Synoptic Repeater R3', 'SE-RP-R3', 219, 30, 'hw', 'Mesh-network signal repeater for large facilities.'],
    ['Synoptic Starter Kit', 'SE-KIT-START', 999, 20, 'hw', 'Gateway + 5 sensor nodes bundle for pilots.'],
    ['Replacement Battery Pack B5', 'SE-BAT-B5', 39, 150, 'hw', 'Long-life battery pack for sensor nodes.'],
    ['Synoptic Cloud Platform — Pro (annual)', 'SE-SW-PRO', 4999, null, 'sw', 'Annual subscription: dashboards, alerting, historian (up to 250 devices).'],
    ['Synoptic Cloud Platform — Enterprise (annual)', 'SE-SW-ENT', 14999, null, 'sw', 'Annual subscription: unlimited devices, SSO, RBAC, SLA.'],
    ['Synoptic Analytics Add-on', 'SE-SW-AN', 2499, null, 'sw', 'Predictive-maintenance analytics module.'],
    ['Premium Support Plan (annual)', 'SE-SUP-PREM', 3499, null, 'sw', '24/7 support with 1-hour response SLA.'],
    ['On-site Installation & Commissioning', 'SE-SVC-INSTALL', 1800, null, 'sw', 'Professional on-site deployment per facility.'],
]
const WAREHOUSES = [
    ['East Distribution Center', 'Karen Doyle', 'east-dc@synoptic.example.com', '85 Dock Rd, Newark, NJ', ['Aisle A / Bay 1', 'Aisle A / Bay 2']],
    ['West Distribution Center', 'Felix Wong', 'west-dc@synoptic.example.com', '300 Harbor Way, Oakland, CA', ['Zone 1 / Rack 1', 'Zone 1 / Rack 2']],
    ['Central Fulfillment Hub', 'Maria Lopez', 'central@synoptic.example.com', '4500 Cargo Ave, Chicago, IL', ['Floor 1 / Shelf A']],
]
const TAGS = [['Enterprise', 'blue'], ['SMB', 'green'], ['Hot', 'red'], ['Renewal', 'amber'], ['Hardware', 'violet'], ['Software', 'cyan'], ['Priority', 'orange'], ['Partner', 'teal']]
const LEADS = [ // [title, amount, stageIdx 0..5 = New/Qualified/Proposal/Negotiation/Won/Lost]
    ['Edge Gateway rollout — 50 sites', 185000, 3], ['Cloud Platform Enterprise renewal', 14999, 4],
    ['Predictive maintenance pilot', 28500, 2], ['Sensor network expansion', 42000, 1],
    ['Power monitoring upgrade', 19800, 0], ['Facility-wide vibration monitoring', 67000, 2],
    ['Starter kit evaluation', 4995, 0], ['Multi-site analytics deployment', 124000, 3],
    ['Warehouse condition monitoring', 31500, 1], ['Support plan upsell', 3499, 4],
    ['Greenfield plant instrumentation', 210000, 2], ['Repeater mesh for campus', 8760, 0],
    ['Annual Pro subscription', 4999, 4], ['Cold-chain temperature compliance', 53200, 1],
    ['Retrofit legacy PLC line', 96500, 2], ['IoT proof-of-concept', 12000, 0],
    ['Enterprise SSO + RBAC migration', 22000, 3], ['Replacement hardware refresh', 15400, 1],
    ['Regional distribution analytics', 38900, 2], ['Lost to competitor — price', 45000, 5],
]

async function wipe() {
    const eps = [
        ['/activities', '/activities/mass-destroy'], ['/quotes', '/quotes/mass-destroy'],
        ['/leads', '/leads/mass-destroy'], ['/contacts/persons', '/contacts/persons/mass-destroy'],
        ['/contacts/organizations', '/contacts/organizations/mass-destroy'],
        // NOTE: products and warehouses are intentionally NOT wiped.
        //  - Products soft-delete, but uq_products_tenant_sku does not exclude
        //    soft-deleted rows, so a deleted SKU stays reserved and recreating it 500s.
        //  - The low-stock query sums inventory across soft-deleted warehouses, so
        //    wiping + recreating warehouses would accumulate stale stock and inflate totals.
        // Both are handled idempotently below (reuse-by-name/SKU) instead.
    ]
    for (const [list, del] of eps) {
        const ids = ((await api('GET', `${list}?size=500`)).json?.content ?? []).map(x => x.id)
        if (ids.length) await api('POST', del, { ids })
    }
    const tags = (await api('GET', '/tags')).json ?? []
    if (tags.length) await api('POST', '/tags/mass-destroy', { ids: tags.map(t => t.id) })
    console.log('✓ wiped existing demo data')
}

async function main() {
    TOKEN = (await api('POST', '/auth/login', { email: EMAIL, password: PASSWORD })).json.accessToken
    if (!TOKEN) throw new Error('login failed')
    console.log('✓ logged in as', EMAIL)
    if (RESET) await wipe()

    const pipeline = (await api('GET', '/pipelines')).json[0]
    const stages = pipeline.stages
    const sources = (await api('GET', '/lead-sources')).json
    const types = (await api('GET', '/lead-types')).json
    const userId = (await api('GET', '/users')).json[0].id

    const T = {}
    for (const [name, color] of TAGS) { const r = await api('POST', '/tags', { name, color }); if (r.ok) T[name] = r.json.id }

    const orgs = []
    for (const [name, address, phone] of ORGS) {
        const r = await api('POST', '/contacts/organizations', { name, address, phone, website: `https://${name.toLowerCase().replace(/[^a-z]+/g, '')}.example.com`, email: `info@${name.toLowerCase().replace(/[^a-z]+/g, '')}.example.com` })
        if (r.ok) orgs.push(r.json)
    }
    const persons = []
    for (let i = 0; i < PEOPLE.length; i++) {
        const [first, last, jobTitle] = PEOPLE[i]; const org = pick(orgs, i)
        const domain = org.name.toLowerCase().replace(/[^a-z]+/g, '') + '.example.com'
        const r = await api('POST', '/contacts/persons', { firstName: first, lastName: last, jobTitle, organizationId: org.id, email: `${first.toLowerCase()}.${last.toLowerCase()}@${domain}`, phone: `+1-555-${String(1000 + i).padStart(4, '0')}` })
        if (r.ok) persons.push(r.json)
    }
    // Idempotent by SKU (see wipe() note): reuse active products, create only missing ones.
    const bySku = Object.fromEntries(((await api('GET', '/products?size=500')).json.content ?? []).map(p => [p.sku, p]))
    const products = []
    for (const [name, sku, price, reorderThreshold, kind, description] of PRODUCTS) {
        let prod = bySku[sku]
        if (!prod) {
            const r = await api('POST', '/products', { name, sku, price, isActive: true, description, reorderThreshold })
            if (r.ok) prod = r.json
        }
        if (prod) products.push({ ...prod, kind })
    }
    // Idempotent by name (see wipe() note): reuse active warehouses + their locations.
    const whByName = Object.fromEntries(((await api('GET', '/warehouses?size=500')).json.content ?? []).map(w => [w.name, w]))
    const warehouses = []
    for (const [name, contactName, contactEmail, contactAddress, locs] of WAREHOUSES) {
        let wh = whByName[name]
        if (!wh) {
            const r = await api('POST', '/warehouses', { name, contactName, contactEmail, contactAddress, description: `${name} — regional stock and fulfillment.` })
            if (!r.ok) continue
            wh = r.json
        }
        let locations = (await api('GET', `/warehouses/${wh.id}/locations`)).json
        locations = Array.isArray(locations) ? locations : (locations?.content ?? [])
        if (locations.length === 0) {
            for (const ln of locs) { const lr = await api('POST', `/warehouses/${wh.id}/locations`, { name: ln }); if (lr.ok) locations.push(lr.json) }
        }
        wh.locations = locations
        warehouses.push(wh)
    }
    // Stock hardware; under-stock three SKUs so the Reorder page tells a real story.
    const LOW = { 'SE-SN-S200': [25, 20, 15], 'SE-RP-R3': [8, 6, 4], 'SE-BAT-B5': [40, 30, 25] }
    for (const p of products.filter(p => p.kind === 'hw')) {
        const low = LOW[p.sku]
        for (let w = 0; w < warehouses.length; w++) {
            const wh = warehouses[w]
            const qty = low ? (low[w] ?? 5) : Math.round((p.reorderThreshold || 50) * (1.6 + (w % 2)))
            await api('PUT', `/products/${p.id}/inventory`, { warehouseId: wh.id, warehouseLocationId: wh.locations[0]?.id, quantity: qty })
        }
    }
    console.log(`✓ ${orgs.length} orgs, ${persons.length} persons, ${products.length} products, ${warehouses.length} warehouses + stock`)

    const leads = []
    for (let i = 0; i < LEADS.length; i++) {
        const [base, amount, st] = LEADS[i]; const org = pick(orgs, i)
        const person = persons.find(p => p.organizationId === org.id) ?? pick(persons, i)
        const r = await api('POST', '/leads', {
            title: `${org.name.split(' ')[0]} — ${base}`, description: `${base} opportunity with ${org.name}.`,
            amount, expectedCloseDate: dstr(day(st >= 4 ? -(5 + i) : 7 + i * 4)),
            pipelineId: pipeline.id, stageId: stages[st].id, personId: person.id, organizationId: org.id,
            leadSourceId: pick(sources, i).id, leadTypeId: pick(types, i).id, userId,
        })
        if (r.ok) leads.push({ ...r.json, st })
    }
    // Won/Lost status so the dashboard shows real revenue.
    const won = leads.filter(l => l.st === 4).map(l => l.id)
    const lost = leads.filter(l => l.st === 5).map(l => l.id)
    if (won.length) await api('POST', '/leads/mass-update', { ids: won, status: 'WON' })
    if (lost.length) await api('POST', '/leads/mass-update', { ids: lost, status: 'LOST' })
    console.log(`✓ ${leads.length} leads (won ${won.length}, lost ${lost.length})`)

    let quotes = 0
    const ql = leads.filter(l => [2, 3, 4].includes(l.st)).slice(0, 7)
    for (let i = 0; i < ql.length; i++) {
        const lead = ql[i]; const items = []
        for (let j = 0; j < 2 + (i % 3); j++) { const p = pick(products, i * 2 + j); items.push({ productId: p.id, quantity: 1 + ((i + j) % 5), unitPrice: p.price, discount: j === 0 ? 5 : 0 }) }
        const r = await api('POST', '/quotes', { leadId: lead.id, title: `Quote — ${lead.title}`, userId, personId: lead.personId, discount: 0, tax: 8.5, adjustment: 0, terms: 'Net 30. Prices valid for 30 days. Shipping FOB origin.', expiredAt: iso(day(30)), items })
        if (r.ok) quotes++
    }
    console.log(`✓ ${quotes} quotes`)

    // General activity spread.
    const A = { CALL: ['Discovery call', 'Follow-up call', 'Pricing discussion'], MEETING: ['On-site demo', 'Technical deep-dive', 'Kickoff meeting'], TASK: ['Send proposal', 'Prepare ROI deck', 'Schedule site survey'], NOTE: ['Budget approved for Q3', 'Champion changed roles'] }
    const atypes = Object.keys(A)
    let acts = 0
    for (let i = 0; i < 14; i++) {
        const type = pick(atypes, i); const lead = pick(leads, i + 1)
        const body = { title: pick(A[type], i), type, comment: `${type} regarding "${lead.title}".`, leadId: lead.id, userId, personId: lead.personId }
        if (type !== 'NOTE') { const s = day(i % 2 ? -(1 + i) : 2 + i); body.scheduleFrom = iso(s); body.scheduleTo = iso(new Date(s.getTime() + 36e5)) }
        if ((await api('POST', '/activities', body)).ok) acts++
    }

    // Hero lead: rich timeline + tags.
    const hero = leads.find(l => /Edge Gateway rollout/.test(l.title)) ?? leads[0]
    const heroActs = [
        ['CALL', 'Discovery call', 'Initial discovery — mapped 50 sites and current monitoring gaps.', -18],
        ['MEETING', 'On-site technical demo', 'Demoed Edge Gateway X1 + analytics at HQ. Strong interest from ops.', -11],
        ['TASK', 'Send proposal & ROI deck', 'Prepare multi-site rollout proposal with phased pricing.', -6],
        ['NOTE', 'Budget approved for Q3', 'CFO confirmed budget; procurement to issue PO after security review.', -3],
        ['MEETING', 'Contract & pricing negotiation', 'Align on volume discount and SLA tier.', 4],
        ['CALL', 'Follow-up on security review', 'Answer infosec questionnaire; schedule pilot site.', 8],
    ]
    for (const [type, title, comment, off] of heroActs) {
        const body = { title, type, comment, leadId: hero.id, userId, personId: hero.personId, organizationId: hero.organizationId }
        if (type !== 'NOTE') { body.scheduleFrom = iso(day(off)); body.scheduleTo = iso(new Date(day(off).getTime() + 36e5)) }
        if (type === 'MEETING') body.location = 'Customer site'
        if ((await api('POST', '/activities', body)).ok) acts++
    }
    console.log(`✓ ${acts} activities (incl. hero timeline on "${hero.title}")`)

    // Tag assignment.
    const addTag = (kind, id, name) => T[name] && api('POST', `/${kind}/${id}/tags`, { tagId: T[name] }, true)
    for (const l of leads) {
        const tg = []
        if (/renewal|subscription|upsell|support/i.test(l.title)) tg.push('Renewal')
        if (/gateway|sensor|repeater|power|vibration|kit|battery|instrumentation|hardware/i.test(l.title)) tg.push('Hardware')
        if (/platform|analytics|sso|cloud|proof/i.test(l.title)) tg.push('Software')
        tg.push(/enterprise|multi-site|greenfield|rollout|plant/i.test(l.title) ? 'Enterprise' : 'SMB')
        for (const t of [...new Set(tg)].slice(0, 3)) await addTag('leads', l.id, t)
    }
    for (const t of ['Enterprise', 'Hardware', 'Hot']) await addTag('leads', hero.id, t)
    const rot = ['Enterprise', 'SMB', 'Hot', 'Priority', 'Partner']
    for (let i = 0; i < persons.length; i++) { await addTag('contacts/persons', persons[i].id, rot[i % rot.length]); await addTag('contacts/persons', persons[i].id, i % 3 ? 'Priority' : 'Hot') }
    for (let i = 0; i < orgs.length; i++) { await addTag('contacts/organizations', orgs[i].id, rot[i % rot.length]); await addTag('contacts/organizations', orgs[i].id, 'Partner') }
    console.log('✓ tags assigned')

    // ── Settings & extras (idempotent: reuse-by-name / skip-if-already-present) ──
    const listOf = async (p) => { const r = (await api('GET', p)).json; return Array.isArray(r) ? r : (r?.content ?? []) }

    // Groups
    const gByName = new Set((await listOf('/groups')).map((g) => g.name))
    for (const [name, description] of [
        ['Sales Team', 'Field and inside sales representatives'],
        ['Account Management', 'Renewals and customer success'],
        ['Operations', 'Fulfillment and inventory operations'],
    ]) if (!gByName.has(name)) await api('POST', '/groups', { name, description })
    console.log('✓ groups')

    // Users (role names; idempotent by email)
    const uByEmail = new Set((await listOf('/users')).map((u) => u.email))
    for (const [email, firstName, lastName, roles] of [
        ['sofia.reyes@synoptic.dev', 'Sofia', 'Reyes', ['MANAGER']],
        ['daniel.cho@synoptic.dev', 'Daniel', 'Cho', ['SALESPERSON']],
        ['amara.okoye@synoptic.dev', 'Amara', 'Okoye', ['SALESPERSON']],
        ['victor.lim@synoptic.dev', 'Victor', 'Lim', ['VIEWER']],
    ]) if (!uByEmail.has(email)) await api('POST', '/users', { email, password: 'Demo@12345', firstName, lastName, roles })
    console.log('✓ users')

    // Email templates (idempotent by name)
    const tplByName = new Set((await listOf('/settings/email-templates')).map((t) => t.name))
    for (const [name, subject, content] of [
        ['Welcome', 'Welcome to Synoptic, {{firstName}}', '<p>Hi {{firstName}},</p><p>Thanks for your interest in Synoptic Engine. Your trial is ready — reply any time with questions.</p>'],
        ['Quote follow-up', 'Following up on your proposal', '<p>Hi {{firstName}},</p><p>Just checking in on the proposal we sent. Happy to adjust scope or pricing — when works for a quick call?</p>'],
        ['Renewal reminder', 'Your Synoptic subscription renews soon', '<p>Hi {{firstName}},</p><p>Your plan renews on {{renewalDate}}. Let us know if you would like to review usage or upgrade.</p>'],
    ]) if (!tplByName.has(name)) await api('POST', '/settings/email-templates', { name, subject, content })
    console.log('✓ email templates')

    // Custom attributes / EAV (idempotent by entityType+code)
    for (const [code, adminName, type, entityType] of [
        ['linkedin_url', 'LinkedIn URL', 'TEXT', 'Person'],
        ['industry', 'Industry', 'TEXT', 'Organization'],
        ['lead_priority', 'Priority', 'TEXT', 'Lead'],
        ['warranty_months', 'Warranty (months)', 'TEXT', 'Product'],
    ]) {
        const have = (await listOf(`/settings/attributes?entityType=${entityType}`)).some((a) => a.code === code)
        if (!have) await api('POST', '/settings/attributes', { code, adminName, type, entityType, sortOrder: 0, userDefined: true })
    }
    console.log('✓ custom attributes')

    // Webhooks (idempotent by name)
    const hookByName = new Set((await listOf('/settings/webhooks')).map((h) => h.name))
    for (const [name, payloadUrl, events] of [
        ['Lead notifications', 'https://example.com/webhooks/leads', ['lead.created', 'lead.updated']],
        ['Quote events', 'https://example.com/webhooks/quotes', ['quote.created']],
    ]) if (!hookByName.has(name)) await api('POST', '/settings/webhooks', { name, payloadUrl, events, isActive: true })
    console.log('✓ webhooks')

    // Marketing: one event + one campaign (idempotent by name)
    let evt = (await listOf('/settings/marketing/events')).find((e) => e.name === 'Q3 Product Webinar')
    if (!evt) { const r = await api('POST', '/settings/marketing/events', { name: 'Q3 Product Webinar', description: 'Live demo of the Synoptic platform and Q&A.', eventDate: dstr(day(21)) }); if (r.ok) evt = r.json }
    if (!(await listOf('/settings/marketing/campaigns')).some((c) => c.name === 'Q3 Webinar Invite')) {
        const tpl = (await listOf('/settings/email-templates'))[0]
        await api('POST', '/settings/marketing/campaigns', { name: 'Q3 Webinar Invite', subject: "You're invited: Synoptic Q3 webinar", description: 'Invite warm leads to the Q3 webinar.', eventId: evt?.id, emailTemplateId: tpl?.id })
    }
    console.log('✓ marketing event + campaign')

    // Inventory movements (reserve/release) — only seed once.
    const hw = products.filter((p) => p.kind === 'hw' && warehouses[0]?.locations[0])
    if ((await listOf('/inventory/movements')).length === 0) {
        for (let i = 0; i < 3 && i < hw.length; i++) {
            const loc = warehouses[0].locations[0]
            await api('POST', '/inventory/reserve', { productId: hw[i].id, locationId: loc.id, qty: 2 + i })
            if (i === 0) await api('POST', '/inventory/release', { productId: hw[i].id, locationId: loc.id, qty: 1 })
        }
    }
    // Stock transfers between warehouses — only seed once.
    if ((await listOf('/inventory/transfers')).length === 0 && warehouses.length >= 2 && hw.length) {
        for (let i = 0; i < 2 && i < hw.length; i++) {
            await api('POST', '/inventory/transfers', {
                productId: hw[i].id, fromLocationId: warehouses[0].locations[0].id,
                toLocationId: warehouses[1].locations[0].id, quantity: 3 + i,
                notes: `Rebalance ${hw[i].name} to ${warehouses[1].name}`,
            })
        }
    }
    console.log('✓ inventory movements + transfers')

    // Mail — only seed once.
    if ((await listOf('/mail')).length === 0) {
        const mails = [
            ['diane.nguyen@northwindtraders.example.com', 'Re: Edge Gateway rollout proposal', 'Hi Diane — attaching the multi-site proposal as discussed. Happy to walk through phased pricing this week.', ['sent'], false],
            ['marcus.bennett@globexcorporation.example.com', 'Synoptic platform — technical questions', 'Thanks for the demo. A few follow-ups on the analytics module and SSO before we proceed.', ['inbox'], false],
            ['renewals@synoptic.dev', 'Draft: Q3 renewal outreach', 'Draft note to the renewals list for the upcoming quarter…', ['drafts'], true],
        ]
        for (const [to, subject, body, folders, isDraft] of mails) await api('POST', '/mail', { to, subject, body, folders, isDraft, attachmentIds: [] })
    }
    console.log('✓ mail')

    // System config (per-tenant settings shown on the Config page) — idempotent PUTs.
    for (const [code, value] of [
        ['general.company_name', 'Synoptic Engine'],
        ['general.company_email', 'hello@synoptic.dev'],
        ['general.company_phone', '+1-415-555-0100'],
        ['general.company_address', '500 Howard St, San Francisco, CA 94105'],
        ['general.locale', 'en-US'],
        ['general.timezone', 'America/Los_Angeles'],
        ['mail.from_name', 'Synoptic Engine'],
        ['mail.from_address', 'noreply@synoptic.dev'],
        ['mail.host', 'localhost'],
        ['mail.port', '1025'],
    ]) await api('PUT', `/settings/config/${code}`, { value })
    console.log('✓ system config')

    console.log('\n=== demo seed complete ===')
}
main().catch(e => { console.error('FATAL', e); process.exit(1) })
