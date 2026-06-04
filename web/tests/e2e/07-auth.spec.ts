import { expect, test } from '@playwright/test'
import { login } from './helpers/auth'

test.describe('Authentication', () => {
    test('logs in and leaves the login page', async ({ page }) => {
        await login(page)
        await expect(page).not.toHaveURL(/\/login/)
        // The authenticated shell renders the primary nav.
        await expect(page.getByRole('link', { name: 'Leads' })).toBeVisible()
    })

    test('rejects invalid credentials and stays on login', async ({ page }) => {
        await page.goto('/login')
        // pressSequentially drives reactivity through NuxtUI's UInput (fill() does not).
        await page.locator('input[type="email"]').click()
        await page.locator('input[type="email"]').pressSequentially('admin@synoptic.dev')
        await page.locator('input[type="password"]').click()
        await page.locator('input[type="password"]').pressSequentially('definitely-wrong')
        await page.getByRole('button', { name: 'Sign in' }).click()
        // No redirect — the sign-in form is still present.
        await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
        await expect(page).toHaveURL(/\/login/)
    })

    test('redirects unauthenticated users away from a protected page', async ({ page }) => {
        await page.goto('/leads')
        await expect(page).toHaveURL(/\/login/)
    })
})
