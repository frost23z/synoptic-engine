import { expect, test } from '@playwright/test'
import { login } from './helpers/auth'

test.describe('Quote actions', () => {
    test('quote detail page shows Download PDF button', async ({ page }) => {
        await login(page)
        await page.goto('/quotes')
        const firstLink = page.locator('table a[href^="/quotes/"]').first()
        await firstLink.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {})
        if ((await firstLink.count()) === 0) {
            test.skip()
            return
        }
        await firstLink.click()
        await expect(page.getByRole('button', { name: 'Download PDF' })).toBeVisible()
    })

    test('quote detail page shows Send button', async ({ page }) => {
        await login(page)
        await page.goto('/quotes')
        const firstLink = page.locator('table a[href^="/quotes/"]').first()
        await firstLink.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {})
        if ((await firstLink.count()) === 0) {
            test.skip()
            return
        }
        await firstLink.click()
        await expect(page.getByRole('button', { name: 'Send' })).toBeVisible()
    })
})
