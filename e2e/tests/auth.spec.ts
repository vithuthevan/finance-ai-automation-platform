import { test, expect } from '@playwright/test';
import { loginAs, logout } from '../support/auth';
import { ADMIN_EMAIL, DEMO_PASSWORD } from '../support/constants';

test.describe('Authentication', () => {
  test('shows the login page', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  });

  test('admin can sign in and reach the dashboard', async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL);
    await expect(page).toHaveURL(/\/app\/dashboard/);
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await expect(page.getByText('Priya Fernando')).toBeVisible();
  });

  test('rejects invalid credentials', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('not-a-user@example.test');
    await page.getByLabel('Password').fill('wrong-password');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByText('Invalid credentials')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('logout returns to login', async ({ page }) => {
    await loginAs(page, ADMIN_EMAIL);
    await logout(page);
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  });
});
