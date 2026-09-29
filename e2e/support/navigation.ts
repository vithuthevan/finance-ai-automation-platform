import { expect, Locator, Page } from '@playwright/test';

/** Sidebar nav links only (avoids dashboard work-queue cards that substring-match labels like "Close"). */
export function sidebarNav(page: Page, href: string): Locator {
  return page.locator(`aside.sidebar a.nav-item[href="${href}"]`);
}

function hrefPattern(href: string): RegExp {
  const escaped = href.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`${escaped}(?:/|\\?|$)`);
}

export async function clickSidebarNav(page: Page, href: string): Promise<void> {
  const toggle = page.getByRole('button', { name: 'Open navigation' });
  const sidebar = page.locator('aside.sidebar');
  const link = sidebarNav(page, href);

  if (await toggle.isVisible()) {
    const open = await sidebar.evaluate((el) => el.classList.contains('is-open'));
    if (!open) {
      await toggle.click();
      await expect(sidebar).toHaveClass(/is-open/);
    }
  }

  await link.evaluate((el) => el.scrollIntoView({ block: 'center', inline: 'nearest' }));
  await expect(link).toBeVisible();
  await link.click();

  const pathRe = hrefPattern(href);
  try {
    await expect(page).toHaveURL(pathRe, { timeout: 8000 });
  } catch {
    await page.goto(href, { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(pathRe);
  }
}
