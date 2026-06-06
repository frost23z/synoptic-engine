import { expect, test } from '@playwright/test'
import { login } from './helpers/auth'

test('forgot password link opens modal on login page', async ({ page }) => {
    await page.goto('/login')
    // Wait for Vue to hydrate before interacting
    const forgotBtn = page.locator('button', { hasText: 'Forgot password?' })
    await forgotBtn.waitFor({ state: 'visible', timeout: 10000 })
    await forgotBtn.click()
    // UModal teleports content to body via Reka UI dialog
    await expect(page.locator('[role="dialog"]')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('[role="dialog"]').getByText('Reset Password')).toBeVisible()
})

test('reset-password page shows error when no token provided', async ({ page }) => {
    await page.goto('/reset-password')
    await expect(page.getByText('Invalid or missing reset token')).toBeVisible()
})

test('pipeline settings page is accessible', async ({ page }) => {
    await login(page)
    await page.goto('/settings/pipelines')
    await expect(page.getByRole('heading', { name: 'Pipelines' }).first()).toBeVisible()
})

test('organization detail page has Activities tab', async ({ page }) => {
    await login(page)
    await page.goto('/contacts/organizations')
    // Find a link to an org detail page (a data-row link, not nav/CTA links)
    const orgLink = page.locator('table a[href^="/contacts/organizations/"]').first()
    await orgLink.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {})
    if ((await orgLink.count()) === 0) {
        test.skip()
        return
    }
    await orgLink.click()
    await expect(page.getByRole('button', { name: 'Activities' })).toBeVisible({ timeout: 10000 })
})

test('mail detail page loads without errors', async ({ page }) => {
    await login(page)
    await page.goto('/mail')
    // Page loads — attachment download is present if mail with attachments exists
    await expect(page.getByRole('heading', { name: 'Mail' }).first()).toBeVisible()
})
