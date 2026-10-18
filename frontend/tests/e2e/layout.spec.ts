import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

test("adapts its theme, sidebar, and navigation at the planned widths", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/tests/fixtures/visual.html");

  await expect(page.getByRole("heading", { name: "Inicio" })).toBeVisible();
  await expect(
    page.getByRole("navigation", { name: "Navegación principal" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Contraer menú" }),
  ).toBeVisible();

  const lightAxe = await new AxeBuilder({ page }).analyze();
  expect(lightAxe.violations).toEqual([]);

  await page.getByRole("button", { name: "Activar tema oscuro" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expect(
    page.getByRole("button", { name: "Activar tema claro" }),
  ).toBeVisible();
  await expect(page.locator(".cg-sidebar .ant-menu-item-selected a")).toHaveCSS(
    "color",
    "rgb(245, 245, 245)",
  );

  const darkAxe = await new AxeBuilder({ page }).analyze();
  expect(darkAxe.violations).toEqual([]);

  for (const width of [1440, 1024, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 850 });
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    expect(
      overflow,
      `No debe haber desbordamiento a ${width}px`,
    ).toBeLessThanOrEqual(1);
  }

  await expect(
    page.getByRole("button", { name: "Abrir navegación" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Abrir navegación" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Cerrar navegación" }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(
    page.getByRole("button", { name: "Abrir navegación" }),
  ).toBeFocused();

  await page.setViewportSize({ width: 1024, height: 850 });
  await expect(
    page.getByRole("button", { name: "Contraer menú" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Contraer menú" }).click();
  await expect(
    page.getByRole("button", { name: "Expandir menú" }),
  ).toBeVisible();
});
