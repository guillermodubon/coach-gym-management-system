import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

test("serves the Spanish application entry point with an accessible main landmark", async ({
  page,
}) => {
  await page.goto("/");

  await expect(page).toHaveTitle("Coach Gym");
  await expect(
    page.getByRole("heading", { level: 1, name: "Coach Gym" }),
  ).toBeAttached();

  const accessibilityResults = await new AxeBuilder({ page }).analyze();

  expect(accessibilityResults.violations).toEqual([]);
});
