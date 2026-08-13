import { test, expect } from '@playwright/test';

test.describe('Global Command Palette (⌘K) Suite', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard', { waitUntil: 'networkidle' });
  });

  test('Trigger Command Palette modal via keyboard shortcut ⌘K', async ({ page }) => {
    const isMac = process.platform === 'darwin';
    await page.keyboard.press(`${isMac ? 'Meta' : 'Control'}+k`);
    
    const searchInput = page.locator('input[placeholder*="Buscar"]').first();
    await expect(searchInput).toBeVisible();
    
    // Type search query
    await searchInput.fill('router');
    
    // Close palette with Escape key
    await page.keyboard.press('Escape');
    await expect(searchInput).toBeHidden();
  });

  test('Trigger Command Palette via TopBar search button click', async ({ page }) => {
    const searchBtn = page.locator('button:has-text("⌘K"), [aria-label="Command Palette"], [data-testid="topbar-search-trigger"]').first();
    await expect(searchBtn).toBeVisible();
    await searchBtn.click();

    const searchInput = page.locator('input[placeholder*="Buscar"]').first();
    await expect(searchInput).toBeVisible();
  });
});
