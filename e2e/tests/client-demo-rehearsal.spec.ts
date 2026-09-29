import path from 'node:path';
import { test, expect } from '@playwright/test';
import { loginAs, logout } from '../support/auth';
import { ADMIN_EMAIL, DEMO_CLIENT, OWNER_EMAIL } from '../support/constants';
import { selectActiveClientIfShown } from '../support/client-context';
import { clickSidebarNav } from '../support/navigation';

const BANK_CSV = path.resolve(__dirname, '../../demo/files/bank-sept.csv');
const UTILITY_PDF = path.resolve(__dirname, '../../demo/files/utility-bill.pdf');

test.describe.configure({ mode: 'serial' });

test.describe('Client demo rehearsal (Harbor Ledger / Cedar Café)', () => {
  const serverErrors: string[] = [];

  test.beforeEach(async ({ page }) => {
    page.on('response', (res) => {
      if (res.status() >= 500) {
        serverErrors.push(`${res.status()} ${res.url()}`);
      }
    });
    page.on('pageerror', (err) => serverErrors.push(`PAGEERROR: ${err.message}`));
  });

  test('full golden path through UI', async ({ page }) => {
    test.setTimeout(300_000);
    await loginAs(page, ADMIN_EMAIL);
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible({ timeout: 20000 });

    await clickSidebarNav(page, '/app/documents');
    await expect(page).toHaveURL(/\/app\/documents/);
    const reviewBtn = page.getByRole('button', { name: 'Review' });
    const emptyInbox = page.getByText('No documents match your filters');
    await expect(reviewBtn.or(emptyInbox)).toBeVisible({ timeout: 20000 });
    if (await reviewBtn.isVisible()) {
      await reviewBtn.click();
      await expect(page.getByRole('button', { name: 'Accept as draft' })).toBeVisible({ timeout: 15000 });

      const reviewForm = page.locator('form').filter({ has: page.getByRole('button', { name: 'Accept as draft' }) });
      await reviewForm.getByRole('textbox', { name: 'Date' }).fill('9/5/2026');
      await reviewForm.getByLabel('Category').click();
      await page.getByRole('option', { name: /Food & beverage supplies/i }).click();
      await page.getByLabel('Amount').fill('4850');
      await page.getByLabel('Party').fill('Keells Super');
      await expect(page.getByRole('button', { name: 'Accept as draft' })).toBeEnabled({ timeout: 5000 });
      await page.getByRole('button', { name: 'Accept as draft' }).click();
      await expect(page.getByText('Draft created from suggestion')).toBeVisible({ timeout: 15000 });
    }

    await clickSidebarNav(page, '/app/expenses');
    await selectActiveClientIfShown(page, DEMO_CLIENT);
    await page.getByLabel('Status').click();
    await page.getByRole('option', { name: 'Draft' }).click();
    await expect(page.getByRole('button', { name: 'Approve' }).first()).toBeVisible({ timeout: 15000 });
    await page.getByRole('button', { name: 'Approve' }).first().click();
    await expect(page.getByText('APPROVED').first()).toBeVisible({ timeout: 15000 });

    await clickSidebarNav(page, '/app/income');
    await selectActiveClientIfShown(page, DEMO_CLIENT);
    const incomeDraft = page.locator('section.card-block').filter({ hasText: 'Create draft' });
    await incomeDraft.getByLabel('Date').fill('9/8/2026');
    await incomeDraft.getByLabel('Category').click();
    await page.getByRole('option', { name: /Café sales/i }).click();
    await incomeDraft.getByLabel('Amount').fill('18500');
    await incomeDraft.getByLabel('Customer').fill('Card settlement');
    await incomeDraft.getByLabel('Payment').click();
    await page.getByRole('option', { name: 'Card' }).click();
    await incomeDraft.getByRole('button', { name: 'Create draft' }).click();
    await page.getByLabel('Status').click();
    await page.getByRole('option', { name: 'Draft' }).click();
    await page.getByRole('button', { name: 'Approve' }).first().click();
    await expect(page.getByText('APPROVED').first()).toBeVisible({ timeout: 15000 });

    await clickSidebarNav(page, '/app/expenses');
    await selectActiveClientIfShown(page, DEMO_CLIENT);
    const expDraft = page.locator('section.card-block').filter({ hasText: 'Create draft' });
    await expDraft.getByLabel('Date').fill('9/10/2026');
    await expDraft.getByLabel('Category').click();
    await page.getByRole('option', { name: 'Utilities' }).click();
    await expDraft.getByLabel('Amount').fill('6200');
    await expDraft.getByLabel('Vendor').fill('CEB');
    await expDraft.getByRole('button', { name: 'Create draft' }).click();
    await page.getByLabel('Status').click();
    await page.getByRole('option', { name: 'Draft' }).click();
    await page.getByRole('button', { name: 'Approve' }).first().click();
    await expect(page.getByText('APPROVED').first()).toBeVisible({ timeout: 15000 });

    await clickSidebarNav(page, '/app/banking');
    await selectActiveClientIfShown(page, DEMO_CLIENT);
    await page.getByRole('tab', { name: 'Import' }).click();
    await page.locator('input[type="file"]').setInputFiles(BANK_CSV);
    await page.getByRole('button', { name: 'Preview' }).click();
    await expect(page.getByText(/Valid:/)).toBeVisible({ timeout: 20000 });
    await page.getByRole('button', { name: 'Import' }).click();
    await expect(page.getByText('Import completed').or(page.getByText('KEELLS'))).toBeVisible({ timeout: 30000 });

    await page.getByRole('tab', { name: 'Reconciliation' }).click();
    await expect(page.locator('.reconcile-card').first()).toBeVisible({ timeout: 20000 });
    for (const needle of ['KEELLS', 'POS SALE', 'CEB']) {
      const card = page.locator('.reconcile-card').filter({ hasText: needle });
      await card.getByRole('button', { name: 'Confirm' }).click();
      await expect(card.locator('.status-MATCHED')).toBeVisible({ timeout: 15000 });
    }
    const bankFee = page.locator('.reconcile-card').filter({ hasText: 'BANK FEE' });
    await bankFee.scrollIntoViewIfNeeded();
    await bankFee.getByRole('button', { name: 'Ignore' }).click();
    await bankFee.getByLabel('Reason').fill('Demo bank charge — no expense');
    await bankFee.getByRole('button', { name: 'Ignore line' }).click();

    const reconCard = page.locator('.metric-card').filter({ hasText: 'Reconciliation' }).locator('.value');
    await expect(reconCard).toHaveText('100%', { timeout: 15000 });

    await clickSidebarNav(page, '/app/close');
    await selectActiveClientIfShown(page, DEMO_CLIENT);
    await page.getByLabel('Month').click();
    await page.getByRole('option', { name: 'September' }).click();
    await page.getByLabel('Search client').fill('Cedar Café');
    await page.getByRole('button', { name: 'Refresh' }).click();
    await expect(page.getByRole('button', { name: 'Open workspace' })).toHaveCount(1, { timeout: 15000 });
    await page.getByRole('button', { name: 'Open workspace' }).click();
    await expect(page.getByText(/open document request/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole('button', { name: 'Close period' })).toBeDisabled();

    await logout(page);
    await loginAs(page, OWNER_EMAIL);
    await page.getByRole('link', { name: /Requested documents/i }).click();
    await page.locator('input[type="file"]').first().setInputFiles(UTILITY_PDF);
    await page.getByRole('button', { name: 'Upload' }).first().click();
    await expect(page.getByText(/UPLOADED/i)).toBeVisible({ timeout: 20000 });

    await logout(page);
    await loginAs(page, ADMIN_EMAIL);
    await clickSidebarNav(page, '/app/close');
    await selectActiveClientIfShown(page, DEMO_CLIENT);
    await page.getByLabel('Month').click();
    await page.getByRole('option', { name: 'September' }).click();
    await page.getByLabel('Search client').fill('Cedar Café');
    await page.getByRole('button', { name: 'Refresh' }).click();
    await expect(page.getByRole('button', { name: 'Open workspace' })).toHaveCount(1, { timeout: 15000 });
    await page.getByRole('button', { name: 'Open workspace' }).click();
    await page.getByRole('button', { name: 'Mark complete' }).click();

    await page.getByLabel('Close note (optional)').fill('September 2026 demo close after reconciliation');
    await page.getByRole('button', { name: 'Close period' }).click();
    await expect(page.getByText('CLOSED')).toBeVisible({ timeout: 20000 });

    await clickSidebarNav(page, '/app/expenses');
    await selectActiveClientIfShown(page, DEMO_CLIENT);
    const expDraft2 = page.locator('section.card-block').filter({ hasText: 'Create draft' });
    await expDraft2.getByLabel('Date').fill('9/15/2026');
    await expDraft2.getByLabel('Category').click();
    await page.getByRole('option', { name: 'Utilities' }).click();
    await expDraft2.getByLabel('Amount').fill('100');
    await expDraft2.getByLabel('Vendor').fill('Should fail');
    await expDraft2.getByRole('button', { name: 'Create draft' }).click();
    await expect(page.getByText(/period|closed/i).first()).toBeVisible({ timeout: 15000 });

    await clickSidebarNav(page, '/app/reports');
    await page.getByLabel('Client').click();
    await page.getByRole('option', { name: DEMO_CLIENT }).click();
    await page.getByRole('button', { name: 'Run' }).click();
    await expect(page.getByText('18,500').or(page.getByText('18500'))).toBeVisible({ timeout: 15000 });

    await clickSidebarNav(page, '/app/audit');
    await expect(page).toHaveURL(/\/app\/audit/);

    expect(serverErrors, `Unexpected 5xx or page errors: ${serverErrors.join('; ')}`).toEqual([]);
  });
});
