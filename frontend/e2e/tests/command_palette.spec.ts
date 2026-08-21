import { test, expect } from '@playwright/test';

test.describe('Global Command Palette (Ctrl+K) Suite', () => {
  // Use a smaller timeout for the command palette as it should be fast
  test.setTimeout(30000);

  test.beforeEach(async ({ page }) => {
    await navigateToApp(page);
  });

  test('Trigger Command Palette modal via keyboard shortcut Ctrl+K', async ({ page }) => {
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

  test('Trigger Command Palette via search bar click', async ({ page }) => {
    // Wait for the app to be fully loaded
    await page.waitForSelector('[data-testid="topbar-search-trigger"]', { state: 'visible' });
    
    // Find the search button in the top bar - it should have Ctrl+K text or a specific aria-label
    const searchBtn = page.locator('button:has-text("Ctrl+K"), [aria-label="Command Palette"], [data-testid="topbar-search-trigger"]').first();
    await expect(searchBtn).toBeVisible();
    await searchBtn.click();

    const searchInput = page.locator('input[placeholder*="Buscar"]').first();
    await expect(searchInput).toBeVisible();
  });
});
