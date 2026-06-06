import { expect, test } from '@playwright/test'
import { login } from './helpers/auth'

test('movements page loads with product selector and empty prompt', async ({ page }) => {
    await login(page)
    await page.goto('/inventory/movements')
    await expect(page.getByRole('heading', { name: 'Movements' }).first()).toBeVisible()
    // No product selected yet → ledger shows the prompt, not a table.
    await expect(page.getByText('Select a product to view its movement ledger.')).toBeVisible()
})

test('movements ledger loads after selecting a product', async ({ page }) => {
    await login(page)
    await page.goto('/inventory/movements')

    const select = page.locator('select').first()
    await select.waitFor({ state: 'visible', timeout: 10000 })
    const optionCount = await select.locator('option').count()
    // Needs at least one real product option beyond the placeholder.
    if (optionCount <= 1) {
        test.skip()
        return
    }
    await select.selectOption({ index: 1 })

    // Either ledger rows render or the empty-state for that product appears —
    // both prove the query ran without the initial prompt.
    await expect(page.getByText('Select a product to view its movement ledger.')).toBeHidden()
})
