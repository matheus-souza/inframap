import { test, expect } from '@playwright/test';

test('global shortcuts: Cmd+K / Ctrl+K and Escape without crashes', async ({ page }) => {
  const errors: Error[] = [];
  const consoleErrors: string[] = [];

  page.on('pageerror', (err) => errors.push(err));
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text());
    }
  });

  await page.goto('/');

  const loadingScreen = page.locator('#loading-screen');
  await expect(loadingScreen).toHaveCSS('display', 'none', { timeout: 5000 });

  const canvas = page.locator('canvas');
  await expect(canvas).toBeVisible();

  // Wait for initial load and network activity to settle deterministically
  await page.waitForLoadState('networkidle');

  // Dispatch Meta+K or Control+K depending on platform
  const modifier = process.platform === 'darwin' ? 'Meta' : 'Control';
  await page.keyboard.press(`${modifier}+k`);

  // Dispatch Escape to dismiss modal
  await page.keyboard.press('Escape');

  // Assert zero uncaught JavaScript / WASM runtime errors
  expect(errors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});
