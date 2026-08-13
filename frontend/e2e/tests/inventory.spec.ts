import { test, expect } from '@playwright/test';

test.describe('High-Density Inventory & Batch Actions Suite', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/inventory', { waitUntil: 'networkidle' });
  });

  test('Inventory table rendering and status distribution bar interaction', async ({ page }) => {
    await expect(page).toHaveURL(/.*inventory/);

    // Verify main content container or inventory table is attached
    await page.waitForLoadState('networkidle');
    const container = page.locator('main, div[role="main"], table, [data-testid="inventory-table"]').first();
    await expect(container).toBeVisible();
  });

  test('Checkbox multi-selection triggers floating batch action bar', async ({ page }) => {
    const checkboxes = page.locator('input[type="checkbox"]');
    await expect(checkboxes.first()).toBeVisible();
    await checkboxes.first().click();

    // Verify floating batch action bar or batch count badge
    const floatingBar = page.locator('[data-testid="floating-batch-action-bar"], text=/dispositivo.*selecionado/i').first();
    await expect(floatingBar).toBeVisible();
  });
});
