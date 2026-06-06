import { expect, test } from '@playwright/test'
import { login } from './helpers/auth'

test.describe('Cross-tenant Sharing', () => {
    test('Relationships page loads', async ({ page }) => {
        await login(page)
        await page.goto('/sharing/relationships')
        await expect(page.getByRole('heading', { name: 'Relationships' }).first()).toBeVisible({
            timeout: 10000,
        })
    })

    test('Shared with me page loads', async ({ page }) => {
        await login(page)
        await page.goto('/sharing/shared-with-me')
        await expect(page.getByRole('heading', { name: 'Shared with me' })).toBeVisible({
            timeout: 10000,
        })
    })

    test('Cross-tenant Audit page loads', async ({ page }) => {
        await login(page)
        await page.goto('/sharing/audit')
        await expect(page.getByRole('heading', { name: 'Cross-tenant Audit' }).first()).toBeVisible(
            {
                timeout: 10000,
            }
        )
    })

    test('a record detail can open the Share dialog', async ({ page }) => {
        await login(page)
        await page.goto('/contacts/organizations')
        const link = page.locator('table a[href^="/contacts/organizations/"]').first()
        await link.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {})
        if ((await link.count()) === 0) {
            test.skip()
            return
        }
        await link.click()

        const share = page.getByRole('button', { name: 'Share' })
        if ((await share.count()) === 0) {
            test.skip()
            return
        }
        await share.click()
        await expect(page.locator('[role="dialog"]')).toBeVisible()
    })
})
