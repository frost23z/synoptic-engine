import { expect, test } from '@playwright/test'
import { login } from './helpers/auth'

test.describe('Form validation', () => {
    test('person create surfaces required-field errors on empty submit', async ({ page }) => {
        await login(page)
        await page.goto('/contacts/persons/create')
        await page.getByRole('button', { name: 'Create Person' }).click()
        await expect(page.getByText('First name is required')).toBeVisible()
        await expect(page.getByText('Last name is required')).toBeVisible()
    })

    test('person create rejects an invalid email', async ({ page }) => {
        await login(page)
        await page.goto('/contacts/persons/create')
        // pressSequentially triggers reactivity through NuxtUI's UInput (fill() does not).
        await page.locator('input[type="email"]').click()
        await page.locator('input[type="email"]').pressSequentially('not-an-email')
        await page.getByRole('button', { name: 'Create Person' }).click()
        await expect(page.getByText('Enter a valid email address')).toBeVisible()
    })

    test('organization create requires a name', async ({ page }) => {
        await login(page)
        await page.goto('/contacts/organizations/create')
        await page.getByRole('button', { name: 'Create Organization' }).click()
        await expect(page.getByText('Name is required')).toBeVisible()
    })
})
