import { expect, test } from '@playwright/test'

// Self-serve signup: a brand-new company can register itself and lands logged-in.
test.describe('Self-serve registration', () => {
    test('creates a workspace and logs the new admin in', async ({ page }) => {
        const unique = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`
        const email = `e2e-signup-${unique}@example.com`

        await page.goto('/register')
        // pressSequentially drives reactivity through NuxtUI's UInput (fill() does not).
        await page.getByRole('textbox', { name: 'Company name' }).click()
        await page
            .getByRole('textbox', { name: 'Company name' })
            .pressSequentially(`E2E Co ${unique}`)
        await page.locator('input[type="email"]').click()
        await page.locator('input[type="email"]').pressSequentially(email)
        const passwords = page.locator('input[type="password"]')
        await passwords.nth(0).click()
        await passwords.nth(0).pressSequentially('password123')
        await passwords.nth(1).click()
        await passwords.nth(1).pressSequentially('password123')

        await page.getByRole('button', { name: 'Create workspace' }).click()

        // Auto-login → off the register page, into the authenticated shell.
        await expect(page).not.toHaveURL(/\/register/, { timeout: 15000 })
        await expect(page.getByRole('link', { name: 'Leads' })).toBeVisible()
    })

    test('surfaces validation errors on empty submit', async ({ page }) => {
        await page.goto('/register')
        await page.getByRole('button', { name: 'Create workspace' }).click()
        // Stays on /register and shows the required-field message(s).
        await expect(page).toHaveURL(/\/register/)
        await expect(page.getByText('Company name is required')).toBeVisible()
    })

    test('mismatched passwords are rejected client-side', async ({ page }) => {
        await page.goto('/register')
        await page.getByRole('textbox', { name: 'Company name' }).click()
        await page.getByRole('textbox', { name: 'Company name' }).pressSequentially('Mismatch Co')
        await page.locator('input[type="email"]').click()
        await page.locator('input[type="email"]').pressSequentially('mismatch@example.com')
        const passwords = page.locator('input[type="password"]')
        await passwords.nth(0).click()
        await passwords.nth(0).pressSequentially('password123')
        await passwords.nth(1).click()
        await passwords.nth(1).pressSequentially('different456')
        await page.getByRole('button', { name: 'Create workspace' }).click()
        await expect(page).toHaveURL(/\/register/)
        await expect(page.getByText('Passwords do not match')).toBeVisible()
    })
})
