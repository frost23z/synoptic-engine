import { expect, test } from '@playwright/test'
import { login } from './helpers/auth'

test.describe('Navigation visibility', () => {
    test('admin sees all nav sections', async ({ page }) => {
        await login(page)
        await expect(page.getByRole('link', { name: 'Leads' })).toBeVisible()
        await expect(page.getByRole('link', { name: 'Persons' })).toBeVisible()
        await expect(page.getByRole('link', { name: 'Quotes' })).toBeVisible()
        await expect(page.getByRole('link', { name: 'Activities' })).toBeVisible()
        await expect(page.getByRole('link', { name: 'Mail', exact: true })).toBeVisible()
        await expect(page.getByRole('link', { name: 'Products' })).toBeVisible()
        await expect(page.getByRole('link', { name: 'Warehouses' })).toBeVisible()
        await expect(page.getByRole('link', { name: 'Users' })).toBeVisible()
    })

    test('leads page shows New Lead button for admin', async ({ page }) => {
        await login(page)
        await page.goto('/leads')
        await expect(page.getByRole('link', { name: 'New Lead' })).toBeVisible()
    })

    test('contacts persons page shows New Person button for admin', async ({ page }) => {
        await login(page)
        await page.goto('/contacts/persons')
        await expect(page.getByRole('link', { name: 'New Person' })).toBeVisible()
    })

    test('quotes page shows New Quote button for admin', async ({ page }) => {
        await login(page)
        await page.goto('/quotes')
        await expect(page.getByRole('link', { name: 'New Quote' })).toBeVisible()
    })
})
