import { test, expect } from '@playwright/test';
import { loginAs } from '../support/auth';
import { ADMIN_EMAIL } from '../support/constants';

test.describe('Critical workflow smoke', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL);
  });

  test('documents inbox loads', async ({ page }) => {
    await page.getByRole('link', { name: 'Documents' }).click();
    await expect(page).toHaveURL(/\/app\/documents/);
    await expect(page.getByRole('heading', { name: /document inbox/i })).toBeVisible();
  });

  test('expenses ledger loads', async ({ page }) => {
    await page.getByRole('link', { name: 'Expenses' }).click();
    await expect(page).toHaveURL(/\/app\/expenses/);
    await expect(page.getByRole('heading', { name: /expenses/i })).toBeVisible();
  });

  test('banking workspace loads', async ({ page }) => {
    await page.getByRole('link', { name: 'Banking' }).click();
    await expect(page).toHaveURL(/\/app\/banking/);
    await expect(page.getByRole('heading', { name: /banking/i })).toBeVisible();
  });

  test('period close queue loads', async ({ page }) => {
    await page.getByRole('link', { name: 'Close' }).click();
    await expect(page).toHaveURL(/\/app\/close/);
    await expect(page.getByRole('heading', { name: /month-end close/i })).toBeVisible();
  });

  test('reports summary loads', async ({ page }) => {
    await page.getByRole('link', { name: 'Reports' }).click();
    await expect(page).toHaveURL(/\/app\/reports/);
    await expect(page.getByRole('heading', { name: /report|profit|loss|summary/i })).toBeVisible();
  });
});
