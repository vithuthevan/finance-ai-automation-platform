import { expect, Page } from '@playwright/test';
import { ADMIN_EMAIL, DEMO_PASSWORD } from './constants';

function passwordField(page: Page) {
  return page.getByRole('textbox', { name: 'Password' });
}

export async function loginAs(page: Page, email: string, password = DEMO_PASSWORD): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await passwordField(page).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).not.toHaveURL(/\/login$/);
}

export async function loginAsAdmin(page: Page): Promise<void> {
  await loginAs(page, ADMIN_EMAIL);
}

export async function logout(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Logout' }).click();
  await expect(page).toHaveURL(/\/login$/);
}
