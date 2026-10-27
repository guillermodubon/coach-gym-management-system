import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { responsiveIdentity } from "../fixtures/responsive-identity";
import { waitForVisualTransitions } from "../helpers/visual-stability";
import type { Page } from "@playwright/test";

test.use({
  deviceScaleFactor: 1.5,
  launchOptions: { ignoreDefaultArgs: ["--hide-scrollbars"] },
});

async function mockProfile(page: Page): Promise<void> {
  await page.route("**/api/v1/me/profile", (route) =>
    route.fulfill({
      status: 200,
      json: {
        userId: responsiveIdentity.id,
        username: responsiveIdentity.username,
        email: "operator@example.test",
        firstName: "Administradora",
        lastName: "Regional",
        displayName: responsiveIdentity.fullName,
        roles: responsiveIdentity.roles,
        status: "ACTIVE",
        photoPresent: false,
        photoUrl: null,
        version: 1,
      },
    }),
  );
}

async function expectAccountActionsOnScreen(page: Page): Promise<void> {
  const menu = page.getByRole("menu", { name: "Cuenta" });
  await expect(menu).toBeVisible();
  await waitForVisualTransitions(page);
  await expect(menu).toBeInViewport({ ratio: 1 });
  for (const name of ["Mi perfil", "Ajustes de mi cuenta", "Cerrar sesión"]) {
    const action = page.getByRole("menuitem", { name, exact: true });
    await expect(action).toBeInViewport({ ratio: 1 });
    await expect
      .poll(() =>
        action.evaluate((element) => {
          const rect = element.getBoundingClientRect();
          const hit = document.elementFromPoint(
            rect.left + rect.width / 2,
            rect.top + rect.height / 2,
          );
          return hit !== null && element.contains(hit);
        }),
      )
      .toBe(true);
  }
}

for (const theme of ["light", "dark"] as const) {
  test(`opens the account menu and follows both links with reduced motion in ${theme}`, async ({
    page,
  }) => {
    await page.emulateMedia({ reducedMotion: "reduce" });
    await page.setViewportSize({ width: 332, height: 369 });
    await page.addInitScript((mode) => {
      localStorage.setItem("coach-gym.theme", mode);
    }, theme);
    await page.route("**/api/v1/auth/me", (route) =>
      route.fulfill({ status: 200, json: responsiveIdentity }),
    );
    await mockProfile(page);
    await page.goto("/");
    const navigation = page.getByRole("button", { name: "Abrir navegación" });
    await navigation.click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(page.getByRole("dialog")).toBeHidden();
    await expect(navigation).toBeFocused();
    const account = page.getByRole("button", { name: /^Menú de cuenta de/ });
    await account.click();
    await expectAccountActionsOnScreen(page);
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    await page.getByRole("link", { name: "Mi perfil", exact: true }).click();
    await expect(page).toHaveURL(/\/mi-perfil$/);
    await expect(page.getByLabel("Nombre", { exact: true })).toHaveValue(
      "Administradora",
    );
    await account.click();
    await expectAccountActionsOnScreen(page);
    await page.getByRole("link", { name: "Ajustes de mi cuenta" }).click();
    await expect(page).toHaveURL(/\/mi-cuenta$/);
    await expect(
      page.getByRole("heading", { level: 1, name: "Ajustes de mi cuenta" }),
    ).toBeVisible();
  });

  test(`keeps account actions on screen across compact heights and navigates to the own profile in ${theme}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: 517, height: 498 });
    await page.addInitScript((mode) => {
      localStorage.setItem("coach-gym.theme", mode);
    }, theme);
    await page.route("**/api/v1/auth/me", (route) =>
      route.fulfill({ status: 200, json: responsiveIdentity }),
    );
    await mockProfile(page);

    await page.goto("/");
    await expect(page.getByRole("heading", { name: "Inicio" })).toBeVisible();
    const account = page.getByRole("button", { name: /^Menú de cuenta de/ });
    await account.click();
    await expectAccountActionsOnScreen(page);
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

    for (const viewport of [
      { width: 517, height: 501 },
      { width: 320, height: 480 },
      { width: 837, height: 400 },
      { width: 1280, height: 900 },
      { width: 517, height: 498 },
    ]) {
      await page.setViewportSize(viewport);
      await expectAccountActionsOnScreen(page);
      const beforeClose = await page.evaluate(() => ({
        height: document.documentElement.scrollHeight,
        scrollY: window.scrollY,
      }));
      await account.click();
      await expect(page.getByRole("menu", { name: "Cuenta" })).toHaveCount(0);
      expect(
        await page.evaluate(() => ({
          height: document.documentElement.scrollHeight,
          scrollY: window.scrollY,
        })),
      ).toEqual(beforeClose);
      await account.click();
      await expectAccountActionsOnScreen(page);
    }
    await page.getByRole("link", { name: "Mi perfil", exact: true }).click();
    await expect(page).toHaveURL(/\/mi-perfil$/);
    await expect(page.getByLabel("Nombre", { exact: true })).toHaveValue(
      "Administradora",
    );
    await expect(page.getByLabel("Apellido", { exact: true })).toHaveValue(
      "Regional",
    );
  });

  test(`keeps account actions above content when the header clips its children in ${theme}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: 517, height: 498 });
    await page.addInitScript((mode) => {
      localStorage.setItem("coach-gym.theme", mode);
    }, theme);
    await page.route("**/api/v1/auth/me", (route) =>
      route.fulfill({ status: 200, json: responsiveIdentity }),
    );
    await mockProfile(page);
    await page.goto("/");
    const account = page.getByRole("button", { name: /^Menú de cuenta de/ });
    await expect(account).toBeVisible();
    // A popup must escape layout clipping and stacking contexts of the header.
    await page.locator(".cg-header").evaluate((header) => {
      header.style.contain = "paint";
    });
    await account.click();
    await expectAccountActionsOnScreen(page);
    await page.getByRole("link", { name: "Ajustes de mi cuenta" }).click();
    await expect(page).toHaveURL(/\/mi-cuenta$/);
  });
}

for (const phase of ["pointerdown", "open"] as const) {
  test(`keeps the account menu usable when visible-window focus returns during ${phase}`, async ({
    page,
  }) => {
    let holdRead = false;
    let releaseRead!: () => void;
    let notifyReadStarted!: () => void;
    const readStarted = new Promise<void>((resolve) => {
      notifyReadStarted = resolve;
    });
    const readGate = new Promise<void>((resolve) => {
      releaseRead = resolve;
    });
    await page.route("**/api/v1/auth/me", async (route) => {
      if (holdRead) {
        holdRead = false;
        notifyReadStarted();
        await readGate;
      }
      await route.fulfill({ status: 200, json: responsiveIdentity });
    });
    await mockProfile(page);
    await page.goto("/");
    const account = page.getByRole("button", { name: /^Menú de cuenta de/ });
    const menu = page.getByRole("menu", { name: "Cuenta" });
    await expect(account).toBeVisible();
    if (phase === "open") {
      await account.click();
      await expect(menu).toBeVisible();
    }

    holdRead = true;
    const response = page.waitForResponse("**/api/v1/auth/me");
    try {
      if (phase === "pointerdown") {
        await account.evaluate((button) => {
          button.addEventListener(
            "pointerdown",
            () => {
              window.dispatchEvent(new FocusEvent("focus"));
            },
            { once: true },
          );
        });
        await account.click();
      } else {
        await page.evaluate(() =>
          window.dispatchEvent(new FocusEvent("focus")),
        );
      }
      await readStarted;
      await expect(account).toBeVisible();
      await expect(menu).toBeVisible();
    } finally {
      releaseRead();
      await response;
    }
    await waitForVisualTransitions(page);
    await expect(menu).toBeVisible();
    await page.getByRole("link", { name: "Mi perfil", exact: true }).click();
    await expect(page).toHaveURL(/\/mi-perfil$/);
    await expect(page.getByLabel("Nombre", { exact: true })).toHaveValue(
      "Administradora",
    );
  });
}

test("closes the account menu and clears the previous identity when focus confirms an expired session", async ({
  page,
}) => {
  let expired = false;
  await page.route("**/api/v1/auth/me", (route) =>
    expired
      ? route.fulfill({
          status: 401,
          json: { status: 401, code: "AUTHENTICATION_REQUIRED" },
        })
      : route.fulfill({ status: 200, json: responsiveIdentity }),
  );
  await mockProfile(page);
  await page.goto("/");
  await page.getByRole("button", { name: /^Menú de cuenta de/ }).click();
  await expect(page.getByRole("menu", { name: "Cuenta" })).toBeVisible();

  expired = true;
  await page.evaluate(() => window.dispatchEvent(new FocusEvent("focus")));
  await expect(
    page.getByRole("heading", { name: "Iniciar sesión" }),
  ).toBeVisible();
  await expect(page.getByRole("menu", { name: "Cuenta" })).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: /^Menú de cuenta de/ }),
  ).toHaveCount(0);
  await expect(
    page.getByText(responsiveIdentity.fullName, { exact: true }),
  ).toHaveCount(0);
});
