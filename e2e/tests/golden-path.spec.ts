import { test, expect } from '@playwright/test';
import { loginAs } from '../support/auth';
import { ACCOUNTANT_EMAIL } from '../support/constants';

/**
 * Minimal browser golden-path: ledger draft → approve → report.
 * Depends on demo/seed_demo.py (Cedar Café client + categories).
 * Full document → bank → close flow is covered by backend integration tests.
 */
test.describe('Golden path (browser)', () => {
  const vendor = `E2E Vendor ${Date.now()}`;

  test.beforeEach(async ({ page }) => {
    await loginAs(page, ACCOUNTANT_EMAIL);
  });

  test('accountant creates expense draft, approves it, and sees it in P&L', async ({ page }) => {
    await page.getByRole('link', { name: 'Expenses' }).click();
    await expect(page).toHaveURL(/\/app\/expenses/);

    const clientSelect = page.getByLabel('Select active client');
    await clientSelect.click();
    await page.getByRole('option', { name: /Cedar Café/i }).click();

    const createDraft = page.locator('section.card-block').filter({ hasText: 'Create draft' });
    await createDraft.getByLabel('Amount').fill('1250');
    await createDraft.getByLabel('Vendor').fill(vendor);
    await createDraft.getByLabel('Category').click();
    await page.getByRole('option').first().click();
    await page.getByRole('button', { name: 'Create draft' }).click();

    await page.getByLabel('Status').click();
    await page.getByRole('option', { name: 'Draft' }).click();
    await expect(page.getByText(vendor)).toBeVisible({ timeout: 15000 });

    const approveButton = page.getByRole('button', { name: 'Approve' }).first();
    await approveButton.click();
    await expect(page.getByText('APPROVED').first()).toBeVisible({ timeout: 15000 });

    await page.getByRole('link', { name: 'Reports' }).click();
    await expect(page).toHaveURL(/\/app\/reports/);

    await page.getByLabel('Select active client').click();
    await page.getByRole('option', { name: /Cedar Café/i }).click();
    await page.getByRole('button', { name: 'Run' }).click();

    await expect(page.getByText(/Total Expenses/i)).toBeVisible({ timeout: 15000 });
    await expect(page.locator('.pnl-table')).toBeVisible();
  });
});
