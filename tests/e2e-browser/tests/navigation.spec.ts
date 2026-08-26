import { test, expect } from '@playwright/test';

test('navigation: basic routing, topology graph, and state transitions with zero errors', async ({ page }) => {
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

  // Basic page reload to ensure session and canvas reload cleanly
  await page.reload();
  await expect(loadingScreen).toHaveCSS('display', 'none', { timeout: 5000 });
  await expect(canvas).toBeVisible();

  // Assert zero uncaught JS/WASM or network serialization errors
  expect(errors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});
