import { Page } from '@playwright/test';

/** Shell header client picker (hidden when the user has only one assigned client). */
export async function selectActiveClientIfShown(page: Page, name: string | RegExp): Promise<void> {
  const select = page.getByLabel('Select active client');
  if (await select.isVisible()) {
    await select.click();
    await page.getByRole('option', { name }).click();
  }
}

/** Reports filter client field (hidden when client is locked to a single assignment). */
export async function selectReportClientIfShown(page: Page, name: string | RegExp): Promise<void> {
  const clientField = page.getByLabel('Client');
  if (await clientField.isVisible()) {
    await clientField.click();
    await page.getByRole('option', { name }).click();
  }
}
