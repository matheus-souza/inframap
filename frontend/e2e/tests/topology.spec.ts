import { test, expect } from '@playwright/test';

test.describe('Interactive Topology Map & Canvas Mode Suite', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/topology', { waitUntil: 'networkidle' });
  });

  test('Topology canvas initialization and rendering', async ({ page }) => {
    await expect(page).toHaveURL(/.*topology/);

    // Verify canvas element is visible
    const canvas = page.locator('canvas').first();
    await expect(canvas).toBeVisible();
  });

  test('Canvas node click triggers slide-over Inspector Sheet', async ({ page }) => {
    const canvas = page.locator('canvas').first();
    await expect(canvas).toBeVisible();

    // Click near center of canvas to select node
    await canvas.click({ position: { x: 400, y: 300 } });

    // Assert Inspector Sheet popover or node details container is attached
    const inspectorSheet = page.locator('[data-testid="topology-inspector-sheet"], [aria-label="Inspector Sheet"], div:has-text("Dispositivo")').first();
    await expect(inspectorSheet).toBeVisible();
  });
});
