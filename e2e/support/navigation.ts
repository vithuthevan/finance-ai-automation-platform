import { Page, Locator } from '@playwright/test';

/** Sidebar nav links only (avoids dashboard work-queue cards that substring-match labels like "Close"). */
export function sidebarNav(page: Page, href: string): Locator {
  return page.locator(`aside.sidebar a.nav-item[href="${href}"]`);
}

export async function clickSidebarNav(page: Page, href: string): Promise<void> {
  await sidebarNav(page, href).click();
}
