import { test, expect } from '@playwright/test';
import { loginAs } from '../support/auth';
import { ADMIN_EMAIL, ACCOUNTANT_EMAIL, OWNER_EMAIL } from '../support/constants';

test.describe('Tenant and role workflows', () => {
  test('admin sees practice navigation', async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL);
    await expect(page.getByRole('link', { name: 'Clients' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Banking' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Close' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Users' })).toBeVisible();
  });

  test('accountant can open the work queue', async ({ page }) => {
    await loginAs(page, ACCOUNTANT_EMAIL);
    await page.getByRole('link', { name: 'My work' }).click();
    await expect(page).toHaveURL(/\/app\/work/);
    await expect(page.getByRole('heading', { name: 'My work' })).toBeVisible();
  });

  test('business owner lands on owner home with limited navigation', async ({ page }) => {
    await loginAs(page, OWNER_EMAIL);
    await expect(page).toHaveURL(/\/app\/owner/);
    await expect(page.getByRole('link', { name: 'Home' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'My documents' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Users' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Banking' })).toHaveCount(0);
  });

  test('admin can open clients for the seeded firm', async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL);
    await page.getByRole('link', { name: 'Clients' }).click();
    await expect(page).toHaveURL(/\/app\/clients/);
    await expect(page.getByText('Cedar Café (Pvt) Ltd')).toBeVisible();
  });
});
