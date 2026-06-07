import { expect, test } from '@playwright/test'
import { login } from './helpers/auth'

/**
 * CSV export buttons wire the list pages to the backend `CsvExportController`
 * (`/api/{persons,organizations,leads,products}/export`). The button uses
 * `useDownload().downloadBlob`, which fetches the CSV as a blob and triggers an
 * anchor download — Playwright captures that as a `download` event.
 */
const cases = [
    { path: '/contacts/persons', file: 'persons.csv' },
    { path: '/contacts/organizations', file: 'organizations.csv' },
    { path: '/leads', file: 'leads.csv' },
    { path: '/products', file: 'products.csv' },
] as const

test.describe('CSV export', () => {
    for (const { path, file } of cases) {
        test(`exports ${file} from ${path}`, async ({ page }) => {
            await login(page)
            await page.goto(path)

            const exportButton = page.getByRole('button', { name: 'Export' })
            await expect(exportButton).toBeVisible()

            const [download] = await Promise.all([
                page.waitForEvent('download'),
                exportButton.click(),
            ])
            expect(download.suggestedFilename()).toBe(file)
        })
    }
})
