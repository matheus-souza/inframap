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

  // Verify dedicated #inframap-app container exists and fills viewport
  const appContainer = page.locator('#inframap-app');
  await expect(appContainer).toBeAttached();
  await expect(appContainer).toBeVisible();
  const containerBox = await appContainer.boundingBox();
  expect(containerBox).not.toBeNull();
  expect(containerBox?.x).toBe(0);
  expect(containerBox?.y).toBe(0);
  expect(containerBox?.width).toBe(1440);
  expect(containerBox?.height).toBe(900);

  // Check that canvas is attached inside container and positioned at (0,0) without displacement
  const canvas = page.locator('#inframap-app canvas');
  await expect(canvas).toBeAttached();
  await expect(canvas).toBeVisible();
  const canvasBox = await canvas.boundingBox();
  expect(canvasBox).not.toBeNull();
  expect(canvasBox?.x).toBe(0);
  expect(canvasBox?.y).toBe(0);
  expect(canvasBox?.width).toBeGreaterThanOrEqual(1400);
  expect(canvasBox?.height).toBeGreaterThanOrEqual(800);

  // Validate the initial viewport
  expect(page.viewportSize()?.width).toBe(1440);
  expect(page.viewportSize()?.height).toBe(900);

  // Resize viewport to simulate DevTools toggle / low resolution screen
  await page.setViewportSize({ width: 1024, height: 600 });
  await expect(canvas).toBeVisible();
  const resizedCanvasBox = await canvas.boundingBox();
  expect(resizedCanvasBox?.x).toBe(0);
  expect(resizedCanvasBox?.y).toBe(0);
  expect(resizedCanvasBox?.width).toBe(1024);
  expect(resizedCanvasBox?.height).toBe(600);

  // Restore viewport
  await page.setViewportSize({ width: 1440, height: 900 });
  await expect(canvas).toBeVisible();

  // Assert zero uncaught JavaScript / WASM runtime errors
  expect(errors).toEqual([]);
  expect(consoleErrors).toEqual([]);
});
