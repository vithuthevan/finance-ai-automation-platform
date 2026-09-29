import { Page } from '@playwright/test';

/** Shell header client picker (hidden when the user has only one assigned client). */
export async function selectActiveClientIfShown(page: Page, name: string | RegExp): Promise<void> {
  const select = page.getByLabel('Select active client');
  try {
    await select.waitFor({ state: 'visible', timeout: 8000 });
  } catch {
    return;
  }
  const selected = (await select.innerText()).trim();
  const already = typeof name === 'string' ? selected.includes(name) : name.test(selected);
  if (already) {
    return;
  }
  await select.click();
  await page.getByRole('option', { name }).click();
}

/** Reports filter client field (hidden when client is locked to a single assignment). */
export async function selectReportClientIfShown(page: Page, name: string | RegExp): Promise<void> {
  const clientField = page.getByLabel('Client');
  try {
    await clientField.waitFor({ state: 'visible', timeout: 8000 });
  } catch {
    return;
  }
  await clientField.click();
  await page.getByRole('option', { name }).click();
}
