import { test, expect } from '@playwright/test';

test.describe('InfraMap Excalidraw Slate Dark Navigation Suite', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/', { waitUntil: 'networkidle' });
  });

  test('Slim NavRail route transitions & Dark Canvas background compliance', async ({ page }) => {
    // Navigate to Dashboard
    await page.goto('/dashboard', { waitUntil: 'networkidle' });
    await expect(page).toHaveURL(/.*dashboard/);

    // Evaluate background color token (#121214 slate dark canvas)
    const bgColor = await page.evaluate(() => {
      return window.getComputedStyle(document.body).backgroundColor;
    });
    // RGB for #121214 is rgb(18, 18, 20)
    expect(bgColor).toBe('rgb(18, 18, 20)');

    // Navigate to Topology
    await page.goto('/topology', { waitUntil: 'networkidle' });
    await expect(page).toHaveURL(/.*topology/);

    // Navigate to Inventory
    await page.goto('/inventory', { waitUntil: 'networkidle' });
    await expect(page).toHaveURL(/.*inventory/);
  });
});
