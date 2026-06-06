import { expect, test } from '@playwright/test'
import { login } from './helpers/auth'

const TITLE_PLACEHOLDER = 'e.g. ACME Corp Expansion'

test.describe('Leads CRUD', () => {
    test('New Lead button opens the create form, gated until a title is entered', async ({
        page,
    }) => {
        await login(page)
        await page.goto('/leads')
        await page.getByRole('link', { name: 'New Lead' }).click()
        await expect(page).toHaveURL(/\/leads\/create/)

        const create = page.getByRole('button', { name: 'Create Lead' })
        await expect(create).toBeDisabled()
        const title = page.getByPlaceholder(TITLE_PLACEHOLDER)
        await title.click()
        await title.pressSequentially('Draft lead')
        await expect(create).toBeEnabled()
    })

    test('creates a lead and lands on its detail page', async ({ page }) => {
        await login(page)
        await page.goto('/leads/create')

        const title = `E2E Lead ${Date.now()}`
        const titleInput = page.getByPlaceholder(TITLE_PLACEHOLDER)
        await titleInput.click()
        await titleInput.pressSequentially(title)
        await page.getByRole('button', { name: 'Create Lead' }).click()

        // A successful create routes to /leads/<uuid>.
        await expect(page).toHaveURL(/\/leads\/[0-9a-fA-F-]{36}$/, { timeout: 15000 })
        await expect(page.getByText(title).first()).toBeVisible()
    })

    test('saves an edit from the lead detail page', async ({ page }) => {
        await login(page)
        await page.goto('/leads/create')

        const title = `E2E Edit ${Date.now()}`
        const titleInput = page.getByPlaceholder(TITLE_PLACEHOLDER)
        await titleInput.click()
        await titleInput.pressSequentially(title)
        await page.getByRole('button', { name: 'Create Lead' }).click()
        await expect(page).toHaveURL(/\/leads\/[0-9a-fA-F-]{36}$/, { timeout: 15000 })

        await page.getByRole('button', { name: 'Edit' }).click()
        const dialog = page.locator('[role="dialog"]')
        await expect(dialog).toBeVisible()
        // Saving the (unchanged) form exercises the edit submit + ProblemDetail mapping path.
        await dialog.getByRole('button', { name: 'Save' }).click()
        await expect(dialog).toBeHidden({ timeout: 15000 })
    })
})
