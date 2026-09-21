import { test, expect } from '@playwright/test';
import { loginAsAdmin } from '../support/auth';

test.describe('Invoice to cash', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('admin can open invoicing workspace', async ({ page }) => {
    await page.goto('/app/ar');
    await expect(page.getByRole('heading', { name: /Invoicing & receivables/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Customers' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Invoices' })).toBeVisible();
  });
});
