import { test, expect } from '@playwright/test';

test('smoke test: page loads, canvas attaches and no uncaught runtime errors occur', async ({ page }) => {
  const errors: Error[] = [];
  const consoleErrors: string[] = [];

  page.on('pageerror', (err) => errors.push(err));
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text());
    }
  });

  await page.goto('/');

  // Wait for loading screen to be hidden within 5s
  const loadingScreen = page.locator('#loading-screen');
  await expect(loadingScreen).toBeHidden({ timeout: 5000 });

  // Check that the inframap-canvas is attached and visible
  const canvas = page.locator('#inframap-canvas');
  await expect(canvas).toBeAttached();
  await expect(canvas).toBeVisible();

  // Assert zero uncaught JavaScript / WASM runtime errors
  expect(errors).toEqual([]);
});
