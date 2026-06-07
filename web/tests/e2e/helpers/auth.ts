import type { Page } from '@playwright/test'

export async function login(page: Page) {
    await page.goto('/login')
    // pressSequentially triggers Vue reactivity through NuxtUI's UInput wrapper (fill() does not)
    await page.locator('input[type="email"]').click()
    await page
        .locator('input[type="email"]')
        .pressSequentially(process.env.E2E_EMAIL ?? 'admin@synoptic.dev')
    await page.locator('input[type="password"]').click()
    await page
        .locator('input[type="password"]')
        .pressSequentially(process.env.E2E_PASSWORD ?? '1234')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await page.waitForURL(/^(?!.*\/login)/)
}
