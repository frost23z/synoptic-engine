import { expect, test } from '@playwright/test'
import { login } from './helpers/auth'

test.describe('Dashboard', () => {
    test('renders the header and KPI tiles for admin', async ({ page }) => {
        await login(page)
        await page.goto('/')
        await expect(page.getByRole('heading', { name: 'Dashboard' }).first()).toBeVisible({
            timeout: 10000,
        })
        // KPI tiles (over-all + revenue-stats) render their labels immediately.
        await expect(page.getByText('Total Leads')).toBeVisible()
        await expect(page.getByText('Won Revenue')).toBeVisible()
        await expect(page.getByText('Avg Lead Value')).toBeVisible()
    })

    test('shows the analytical stat widgets', async ({ page }) => {
        await login(page)
        await page.goto('/')
        await expect(page.getByText('Leads Over Time')).toBeVisible({ timeout: 10000 })
        await expect(page.getByText('Open Leads by Stage')).toBeVisible()
        await expect(page.getByText('Revenue by Source')).toBeVisible()
        await expect(page.getByText('Top Selling Products')).toBeVisible()
    })

    test('shows the live recent + upcoming activity panels', async ({ page }) => {
        await login(page)
        await page.goto('/')
        await expect(page.getByText('Recent Activities', { exact: true })).toBeVisible({
            timeout: 10000,
        })
        await expect(page.getByText('Upcoming Activities')).toBeVisible()
    })

    test('exposes the date-range controls', async ({ page }) => {
        await login(page)
        await page.goto('/')
        await expect(page.getByRole('heading', { name: 'Dashboard' }).first()).toBeVisible({
            timeout: 10000,
        })
        // Two native date inputs (start / end) back the custom range.
        await expect(page.locator('input[type="date"]')).toHaveCount(2)
    })
})
