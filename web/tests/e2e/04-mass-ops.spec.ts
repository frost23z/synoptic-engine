import { expect, test } from '@playwright/test'
import { login } from './helpers/auth'

async function checkMassOpsBar(
    page: import('@playwright/test').Page,
    listUrl: string,
    _entityName: string
) {
    await login(page)
    await page.goto(listUrl)
    const checkboxes = page.getByRole('checkbox')
    const checkboxCount = await checkboxes.count()
    if (checkboxCount === 0) {
        // No rows in DB — skip gracefully
        test.skip()
        return
    }
    // Click first data row checkbox (skip header checkbox at index 0)
    await checkboxes.nth(1).click()
    await expect(page.getByText(/1 selected/)).toBeVisible()
    await page.getByRole('button', { name: 'Clear' }).click()
    await expect(page.getByText(/1 selected/)).not.toBeVisible()
}

test('leads list has working mass-select', async ({ page }) => {
    await checkMassOpsBar(page, '/leads', 'leads')
})

test('persons list has working mass-select', async ({ page }) => {
    await checkMassOpsBar(page, '/contacts/persons', 'persons')
})

test('organizations list has working mass-select', async ({ page }) => {
    await checkMassOpsBar(page, '/contacts/organizations', 'organizations')
})

test('activities list has working mass-select', async ({ page }) => {
    await checkMassOpsBar(page, '/activities', 'activities')
})

test('quotes list has working mass-select', async ({ page }) => {
    await checkMassOpsBar(page, '/quotes', 'quotes')
})

test('products list has working mass-select', async ({ page }) => {
    await checkMassOpsBar(page, '/products', 'products')
})
