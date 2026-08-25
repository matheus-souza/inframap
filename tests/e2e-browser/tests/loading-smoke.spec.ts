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
  await expect(loadingScreen).toHaveClass(/hidden/, { timeout: 5000 });
  await expect(loadingScreen).toHaveCSS('display', 'none');

  // Check that canvas is attached and visible
  const canvas = page.locator('canvas');
  await expect(canvas).toBeAttached();
  await expect(canvas).toBeVisible();

  // Validate the initial viewport
  expect(page.viewportSize()?.width).toBe(1440);
  expect(page.viewportSize()?.height).toBe(900);

  // Resize viewport to simulate DevTools toggle / low resolution screen
  await page.setViewportSize({ width: 1024, height: 600 });
  await expect(canvas).toBeVisible();

  // Restore viewport
  await page.setViewportSize({ width: 1440, height: 900 });
  await expect(canvas).toBeVisible();

  // Assert zero uncaught JavaScript / WASM runtime errors
  expect(errors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});
