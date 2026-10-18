import { expect } from "@playwright/test";
import type { Locator, Page } from "@playwright/test";

export async function expectPageToFit(page: Page): Promise<void> {
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          document.documentElement.scrollWidth -
          document.documentElement.clientWidth,
      ),
    )
    .toBeLessThanOrEqual(1);
}

export async function expectWithinScreen(locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await expect
    .poll(() =>
      locator.evaluate((element) => {
        const rect = element.getBoundingClientRect();
        return rect.left >= -1 && rect.right <= window.innerWidth + 1;
      }),
    )
    .toBe(true);
}

export async function expectTouchTarget(locator: Locator): Promise<void> {
  await expectWithinScreen(locator);
  const bounds = await locator.boundingBox();
  expect(bounds?.width).toBeGreaterThanOrEqual(44);
  expect(bounds?.height).toBeGreaterThanOrEqual(44);
}
