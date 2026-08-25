import { test, expect } from '@playwright/test';

test('smoke test: page loads and canvas attaches', async ({ page }) => {
  await page.goto('/');
  
  // Wait for loading screen to be hidden (display: none or .hidden)
  const loadingScreen = page.locator('#loading-screen');
  // Check that the loading screen becomes hidden within 5s
  await expect(loadingScreen).toBeHidden({ timeout: 5000 });

  // Check that the inframap-canvas is attached
  const canvas = page.locator('#inframap-canvas');
  await expect(canvas).toBeAttached();
});
