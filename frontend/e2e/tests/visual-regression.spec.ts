import { test, expect } from '@playwright/test';

test.describe('InfraMap WASM WebApp Visual Regression', () => {
  test.beforeEach(async ({ page }) => {
    // Wait until network is idle and canvas/WASM element is attached
    await page.goto('/', { waitUntil: 'networkidle' });
  });

  test('Onboarding & Initial Launch Flow', async ({ page }) => {
    // Ensure the main canvas or container element is present
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveScreenshot('01-onboarding-initial.png', {
      mask: [page.locator('[data-testid="dynamic-timestamp"]')],
    });
  });

  test('Login Screen Visual Layout', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'networkidle' });
    await expect(page).toHaveScreenshot('02-login-screen.png');
  });

  test('Dashboard Screen Visual Layout', async ({ page }) => {
    await page.goto('/dashboard', { waitUntil: 'networkidle' });
    await expect(page).toHaveScreenshot('03-dashboard-screen.png');
  });

  test('Device List & Management Screen', async ({ page }) => {
    await page.goto('/devices', { waitUntil: 'networkidle' });
    await expect(page).toHaveScreenshot('04-device-list.png');
  });

  test('Staging Queue Screen', async ({ page }) => {
    await page.goto('/staging', { waitUntil: 'networkidle' });
    await expect(page).toHaveScreenshot('05-staging-queue.png');
  });

  test('Subnet List Screen', async ({ page }) => {
    await page.goto('/subnets', { waitUntil: 'networkidle' });
    await expect(page).toHaveScreenshot('06-subnet-list.png');
  });

  test('Interactive Topology Map Canvas', async ({ page }) => {
    await page.goto('/topology', { waitUntil: 'networkidle' });
    
    // Verify canvas is present and visible
    const canvas = page.locator('canvas').first();
    await expect(canvas).toBeVisible();
    await expect(page).toHaveScreenshot('07-topology-map.png');
  });
});
