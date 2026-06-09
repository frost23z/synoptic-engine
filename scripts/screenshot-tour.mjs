#!/usr/bin/env node
/**
 * Synoptic Engine — end-to-end screenshot tour + smoke check.
 *
 * Logs in as the admin, walks every primary page as a real user, and for each:
 *   - captures console errors, page errors, and failed / 4xx-5xx API responses
 *   - saves a 1440×900 screenshot
 * Also captures the Leads kanban board and the enriched detail pages.
 * Writes screenshots/ + screenshots/_errors.json and prints a per-page summary.
 *
 * Usage:
 *   node scripts/screenshot-tour.mjs
 * Env (all optional):
 *   E2E_BASE_URL  default http://localhost:3000
 *   API_BASE      default http://localhost:8090/api
 *   ADMIN_EMAIL / ADMIN_PASSWORD   default admin@synoptic.dev / 1234
 *   OUT_DIR       default <repo>/screenshots
 *
 * Playwright is a `web/` devDependency, so we resolve it from there rather than
 * requiring an install at the repo root.
 */
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import { mkdirSync, writeFileSync } from 'node:fs'

const HERE = dirname(fileURLToPath(import.meta.url))
const require = createRequire(join(HERE, '..', 'web') + '/')
const { chromium } = require('@playwright/test')

const WEB = process.env.E2E_BASE_URL ?? 'http://localhost:3000'
const API = process.env.API_BASE ?? 'http://localhost:8090/api'
const EMAIL = process.env.ADMIN_EMAIL ?? 'admin@synoptic.dev'
const PASSWORD = process.env.ADMIN_PASSWORD ?? '1234'
const OUT = process.env.OUT_DIR ?? join(HERE, '..', 'screenshots')
mkdirSync(OUT, { recursive: true })

// ── resolve real IDs for detail pages via the API ───────────────────────────
async function resolveIds() {
    const login = await fetch(API + '/auth/login', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: EMAIL, password: PASSWORD }),
    }).then((r) => r.json())
    const tok = login.accessToken
    const get = (p) => fetch(API + p, { headers: { Authorization: 'Bearer ' + tok } }).then((r) => r.json())
    const firstId = (r) => (r.content ?? r)[0]?.id
    const leads = (await get('/leads?size=200')).content ?? []
    // "hero" lead = the one with the full activity timeline, if present.
    const hero = leads.find((l) => /Edge Gateway rollout/.test(l.title)) ?? leads[0]
    return {
        lead: hero?.id ?? firstId({ content: leads }),
        person: firstId(await get('/contacts/persons?size=1')),
        org: firstId(await get('/contacts/organizations?size=1')),
        product: firstId(await get('/products?size=1')),
        quote: firstId(await get('/quotes?size=1')),
        warehouse: firstId(await get('/warehouses?size=1')),
        pipeline: firstId(await get('/pipelines')),
    }
}

const ID = await resolveIds()

// label, path, { full?, kanban? }
const ROUTES = [
    ['01-dashboard', '/', { full: true }],
    ['02-leads', '/leads', { full: true }],
    ['02b-leads-kanban', '/leads', { full: true, kanban: true }],
    ['03-lead-detail', `/leads/${ID.lead}`, { full: true }],
    ['04-lead-create', '/leads/create'],
    ['05-persons', '/contacts/persons'],
    ['06-person-detail', `/contacts/persons/${ID.person}`, { full: true }],
    ['07-organizations', '/contacts/organizations'],
    ['08-org-detail', `/contacts/organizations/${ID.org}`, { full: true }],
    ['09-products', '/products'],
    ['10-product-detail', `/products/${ID.product}`, { full: true }],
    ['11-quotes', '/quotes'],
    ['12-quote-detail', `/quotes/${ID.quote}`, { full: true }],
    ['13-activities', '/activities'],
    ['14-warehouses', '/warehouses'],
    ['15-warehouse-detail', `/warehouses/${ID.warehouse}`, { full: true }],
    ['16-inventory-stock', '/inventory/stock'],
    ['17-inventory-movements', '/inventory/movements'],
    ['18-inventory-reorder', '/inventory/reorder'],
    ['19-inventory-transfers', '/inventory/transfers'],
    ['20-mail', '/mail'],
    ['20b-mail-sent', '/mail', { clickText: 'Sent' }],
    ['21-sharing-relationships', '/sharing/relationships'],
    ['22-sharing-shared-with-me', '/sharing/shared-with-me'],
    ['23-sharing-audit', '/sharing/audit'],
    ['24-settings-pipelines', '/settings/pipelines'],
    ['25-settings-pipeline-detail', `/settings/pipelines/${ID.pipeline}`, { full: true }],
    ['26-settings-users', '/settings/users'],
    ['27-settings-roles', '/settings/roles'],
    ['28-settings-groups', '/settings/groups'],
    ['29-settings-sources', '/settings/sources'],
    ['30-settings-types', '/settings/types'],
    ['31-settings-tags', '/settings/tags'],
    ['32-settings-attributes', '/settings/attributes'],
    ['33-settings-email-templates', '/settings/email-templates'],
    ['34-settings-web-forms', '/settings/web-forms'],
    ['35-settings-workflows', '/settings/workflows'],
    ['36-settings-marketing', '/settings/marketing'],
    ['37-settings-webhooks', '/settings/webhooks'],
    ['38-settings-imports', '/settings/imports'],
    ['39-settings-config', '/settings/config'],
    ['40-settings-tenants', '/settings/tenants'],
]

const errors = []
let current = 'startup'
const isNoise = (t = '') => /favicon|devtools|sourcemap|\.map\b|Vue Devtools|hydration/i.test(t)
const note = (kind, text) => errors.push({ page: current, kind, text: String(text).slice(0, 300) })

async function login(page) {
    current = 'login'
    await page.goto(WEB + '/login', { waitUntil: 'networkidle' })
    // The dev server compiles routes on first hit; wait for hydration so typed values
    // register reactively (NuxtUI's UInput needs pressSequentially, not fill()).
    await page.waitForTimeout(2500)
    const email = page.locator('input[type="email"]')
    const pw = page.locator('input[type="password"]')
    await email.click()
    await email.pressSequentially(EMAIL)
    await pw.click()
    await pw.pressSequentially(PASSWORD)
    // Wait until validation enables the submit button (proves the values registered).
    await page
        .waitForFunction(() => {
            const b = [...document.querySelectorAll('button')].find((x) => /sign in/i.test(x.textContent || ''))
            return b && !b.disabled
        }, { timeout: 15000 })
        .catch(() => note('login', 'sign-in button stayed disabled'))
    await page.getByRole('button', { name: 'Sign in' }).click()
    await page.waitForURL(/^(?!.*\/login)/, { timeout: 15000 }).catch(() => note('login', 'no redirect after sign-in'))
    await page.waitForTimeout(1500)
}

async function clickKanban(page) {
    for (const sel of ['button:has([class*="layout-kanban"])', 'button:has(.i-tabler-layout-kanban)', '[class*="layout-kanban"]']) {
        const loc = page.locator(sel).first()
        if (await loc.count()) {
            try {
                await loc.click({ timeout: 3000 })
                return true
            } catch {
                note('02b-leads-kanban', `kanban toggle click failed via ${sel}`)
            }
        }
    }
    return false
}

async function clickText(page, text) {
    for (const loc of [page.getByRole('tab', { name: text }), page.getByRole('button', { name: text }), page.getByText(text, { exact: true })]) {
        if (await loc.count()) {
            try {
                await loc.first().click({ timeout: 3000 })
                return true
            } catch {
                /* try next selector */
            }
        }
    }
    return false
}

async function run() {
    const browser = await chromium.launch({ headless: true })
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 })
    const page = await ctx.newPage()

    page.on('console', (m) => { if (m.type() === 'error' && !isNoise(m.text())) note('console.error', m.text()) })
    page.on('pageerror', (e) => { if (!isNoise(e.message)) note('pageerror', e.message) })
    page.on('requestfailed', (r) => {
        const t = r.failure()?.errorText || ''
        if (!isNoise(r.url()) && !/aborted/i.test(t)) note('requestfailed', `${r.method()} ${r.url()} — ${t}`)
    })
    page.on('response', (res) => {
        if (res.status() >= 400 && res.url().includes('/api/')) note(`http.${res.status()}`, `${res.request().method()} ${res.url().replace(API, '')}`)
    })

    await login(page)
    console.log('logged in →', page.url())

    for (const [label, path, opts = {}] of ROUTES) {
        current = label
        try {
            await page.goto(WEB + path, { waitUntil: 'networkidle', timeout: 20000 })
        } catch (e) {
            note('goto-timeout', e)
        }
        await page.waitForTimeout(900)
        if (opts.kanban) {
            await clickKanban(page)
            await page.waitForTimeout(1200)
        }
        if (opts.clickText) {
            await clickText(page, opts.clickText)
            await page.waitForTimeout(1000)
        }
        try {
            await page.screenshot({ path: join(OUT, `${label}.png`), fullPage: !!opts.full })
        } catch (e) {
            note('screenshot-fail', e)
        }
        const n = errors.filter((x) => x.page === label).length
        console.log(`${n ? '⚠ ' : '✓ '}${label.padEnd(34)} ${page.url().replace(WEB, '')}  (${n} issues)`)
    }

    await browser.close()
    writeFileSync(join(OUT, '_errors.json'), JSON.stringify(errors, null, 2))
    console.log(`\n=== ${errors.length} issue(s) across ${ROUTES.length} pages → ${join(OUT, '_errors.json')} ===`)
    const byPage = {}
    for (const e of errors) (byPage[e.page] ??= new Set()).add(`${e.kind}: ${e.text}`)
    for (const [p, set] of Object.entries(byPage)) {
        console.log(`\n[${p}]`)
        for (const l of set) console.log('   ' + l)
    }
}
run().catch((e) => { console.error('FATAL', e); process.exit(1) })
