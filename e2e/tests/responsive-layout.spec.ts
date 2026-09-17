import { test, expect } from '@playwright/test';
import { loginAs } from '../support/auth';
import { ACCOUNTANT_EMAIL } from '../support/constants';

const WIDTHS = [320, 375, 430, 768, 1024, 1280, 1440, 1920] as const;

const ROUTES = [
  '/app/dashboard',
  '/app/documents',
  '/app/expenses',
  '/app/banking',
  '/app/reports',
  '/app/close',
] as const;

test.describe.configure({ mode: 'serial' });

test.describe('Responsive layout (accountant)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, ACCOUNTANT_EMAIL);
  });

  for (const width of WIDTHS) {
    test(`no page-level horizontal overflow at ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      for (const route of ROUTES) {
        await page.goto(route, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(400);
        const overflow = await page.evaluate(() => {
          const doc = document.documentElement;
          return doc.scrollWidth > doc.clientWidth + 2;
        });
        expect(overflow, `horizontal overflow on ${route} at ${width}px`).toBeFalsy();
      }
    });
  }
});
