import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { waitForVisualTransitions } from "../helpers/visual-stability";
import { responsiveIdentity } from "../fixtures/responsive-identity";
import {
  expectPageToFit,
  expectTouchTarget,
  expectWithinScreen,
} from "./helpers/responsive";

async function expectAccessible(page: Page): Promise<void> {
  await waitForVisualTransitions(page);
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.violations.map((violation) => violation.id)).toEqual([]);
}

for (const mode of ["light", "dark"] as const) {
  test(`reflows the real home, navigation and account menu in ${mode}`, async ({
    page,
  }) => {
    await page.addInitScript((theme) => {
      localStorage.setItem("coach-gym.theme", theme);
    }, mode);
    await page.route("**/api/v1/auth/me", (route) =>
      route.fulfill({ status: 200, json: responsiveIdentity }),
    );
    await page.goto("/");
    await expect(
      page.getByRole("heading", { level: 1, name: "Inicio" }),
    ).toBeVisible();

    const account = page.getByRole("button", { name: /^Menú de cuenta de/ });
    const theme = page.getByRole("button", { name: /^Activar tema/ });
    const refresh = page.getByRole("button", { name: "Actualizar contexto" });

    for (const width of [
      280, 320, 360, 390, 575, 600, 768, 991, 992, 1024, 1280, 1440, 1920, 2560,
    ]) {
      await page.setViewportSize({ width, height: 900 });
      await expectPageToFit(page);
      await expectTouchTarget(account);
      await expectTouchTarget(theme);
      await expectWithinScreen(refresh);
      await expect(page.locator("html")).toHaveAttribute("data-theme", mode);
      // The full operational context must remain readable in the content card.
      await expect(page.locator(".cg-context-headline")).toContainText(
        responsiveIdentity.activeBranch.name,
      );
      await expect
        .poll(() =>
          page
            .locator(".cg-context-card .ant-card-head-title")
            .evaluate((element) => element.scrollWidth - element.clientWidth),
        )
        .toBeLessThanOrEqual(1);
      if (width < 992) {
        await expectTouchTarget(
          page.getByRole("button", { name: "Abrir navegación" }),
        );
      } else {
        await expect(
          page.getByRole("button", { name: "Contraer menú" }),
        ).toBeVisible();
      }
    }

    // Both directions must be audited after the actual color transitions finish.
    for (let transition = 0; transition < 2; transition += 1) {
      await page.getByRole("button", { name: /^Activar tema/ }).click();
      await expectAccessible(page);
    }
    await expect(page.locator("html")).toHaveAttribute("data-theme", mode);

    for (const width of [280, 390, 768, 992, 1440]) {
      await page.setViewportSize({ width, height: 600 });
      await account.click();
      const menu = page.getByRole("menu", { name: "Cuenta" });
      await expectWithinScreen(menu);
      await expect(menu).toContainText(responsiveIdentity.fullName);
      await expectWithinScreen(
        menu.getByRole("menuitem", { name: "Cerrar sesión" }),
      );
      await expectPageToFit(page);
      await page.keyboard.press("Escape");
      await expect(menu).toBeHidden();
      await expect(account).toBeFocused();
    }

    await page.setViewportSize({ width: 320, height: 568 });
    await page.getByRole("button", { name: "Abrir navegación" }).click();
    await expectWithinScreen(page.getByRole("dialog"));
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toBeHidden();
    await page.setViewportSize({ width: 844, height: 320 });
    await refresh.scrollIntoViewIfNeeded();
    await expect(refresh).toBeInViewport();
    await expectAccessible(page);
  });

  test(`keeps identity forms and session actions usable in ${mode}`, async ({
    page,
  }) => {
    await page.addInitScript((theme) => {
      localStorage.setItem("coach-gym.theme", theme);
    }, mode);
    let scenario: "login" | "password" | "unavailable" = "login";
    await page.route("**/api/v1/auth/me", (route) => {
      if (scenario === "password") {
        return route.fulfill({
          status: 200,
          json: { ...responsiveIdentity, passwordChangeRequired: true },
        });
      }
      const status = scenario === "login" ? 401 : 503;
      return route.fulfill({
        status,
        json: {
          status,
          code:
            scenario === "login"
              ? "AUTHENTICATION_REQUIRED"
              : "SERVICE_UNAVAILABLE",
        },
      });
    });

    for (const state of ["login", "password", "unavailable"] as const) {
      scenario = state;
      await page.goto("/");
      await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
      for (const { width, height } of [
        { width: 280, height: 653 },
        { width: 320, height: 568 },
        { width: 390, height: 844 },
        { width: 768, height: 1024 },
        { width: 844, height: 320 },
        { width: 1440, height: 900 },
      ]) {
        await page.setViewportSize({ width, height });
        await expectPageToFit(page);
        const buttons = page.getByRole("main").getByRole("button");
        for (const button of await buttons.all()) {
          await expectWithinScreen(button);
          await button.scrollIntoViewIfNeeded();
          await expect(button).toBeInViewport();
        }
      }
      await page.setViewportSize({ width: 320, height: 568 });
      if (state !== "unavailable") {
        await page
          .getByRole("button", {
            name:
              state === "login" ? "Iniciar sesión" : "Actualizar contraseña",
          })
          .click();
        await expect(
          page.getByText(
            state === "login"
              ? "Escribe tu usuario o correo."
              : "Escribe tu contraseña actual.",
          ),
        ).toBeVisible();
        const input = page.locator("main input").first();
        expect(
          await input.evaluate((element) =>
            Number.parseFloat(getComputedStyle(element).fontSize),
          ),
        ).toBeGreaterThanOrEqual(16);
        await expectPageToFit(page);
      }
      await expectAccessible(page);
    }
  });
}
