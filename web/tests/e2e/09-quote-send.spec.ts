import { expect, test, type Page } from '@playwright/test'
import { login } from './helpers/auth'

/** Open the first quote's detail page; returns false (→ skip) when none exist. */
async function openFirstQuote(page: Page): Promise<boolean> {
    await page.goto('/quotes')
    const link = page.locator('table a[href^="/quotes/"]').first()
    await link.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {})
    if ((await link.count()) === 0) return false
    await link.click()
    await expect(page).toHaveURL(/\/quotes\/[0-9a-fA-F-]{36}$/, { timeout: 10000 })
    return true
}

test.describe('Quote send', () => {
    test('Send opens the send-quote dialog with a recipient field', async ({ page }) => {
        await login(page)
        if (!(await openFirstQuote(page))) {
            test.skip()
            return
        }
        await page.getByRole('button', { name: 'Send' }).click()
        const dialog = page.locator('[role="dialog"]')
        await expect(dialog).toBeVisible()
        await expect(dialog.getByText('Send Quote')).toBeVisible()
        await expect(dialog.getByPlaceholder('recipient@example.com')).toBeVisible()
    })

    test('Send validates the recipient email', async ({ page }) => {
        await login(page)
        if (!(await openFirstQuote(page))) {
            test.skip()
            return
        }
        await page.getByRole('button', { name: 'Send' }).click()
        const dialog = page.locator('[role="dialog"]')
        await expect(dialog).toBeVisible()

        const to = dialog.getByPlaceholder('recipient@example.com')
        await to.click()
        await to.pressSequentially('not-an-email')
        await dialog.getByRole('button', { name: 'Send' }).click()
        await expect(dialog.getByText('Enter a valid email address')).toBeVisible()
    })
})
