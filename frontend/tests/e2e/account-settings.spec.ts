import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

const identity = {
  id: "00000000-0000-4000-8000-000000000003",
  username: "operador-prueba",
  fullName: "Operador de prueba",
  roles: ["ADMIN"],
  organizationScope: "ORGANIZATION",
  activeBranch: null,
  availableBranches: [
    {
      id: "00000000-0000-4000-8000-000000000002",
      organizationId: "00000000-0000-4000-8000-000000000001",
      code: "TEST",
      name: "Sucursal de prueba",
      timezone: "America/El_Salvador",
      initialBranch: true,
    },
  ],
  passwordChangeRequired: false,
};

const newPassword = "synthetic-new-password-value";

test("opens account settings, changes password, and logs in again with the new password", async ({
  page,
}) => {
  let sessionActive = true;
  let csrfIssued = 0;
  let passwordChangeRequests = 0;
  let logoutRequests = 0;
  let loginRequests = 0;

  await page.route("**/api/v1/auth/me", async (route) => {
    if (!sessionActive) {
      await route.fulfill({
        status: 401,
        contentType: "application/problem+json",
        body: JSON.stringify({
          status: 401,
          code: "AUTHENTICATION_REQUIRED",
        }),
      });
      return;
    }
    await route.fulfill({ status: 200, json: identity });
  });

  await page.route("**/api/v1/me/profile", async (route) => {
    await route.fulfill({
      status: 200,
      json: {
        userId: identity.id,
        username: identity.username,
        email: "operator@example.test",
        firstName: "Operador",
        lastName: "Prueba",
        displayName: identity.fullName,
        roles: identity.roles,
        status: "ACTIVE",
        photoPresent: false,
        photoUrl: null,
        version: 1,
      },
    });
  });

  await page.route("**/api/v1/auth/csrf", async (route) => {
    csrfIssued += 1;
    await route.fulfill({
      status: 200,
      json: {
        token: `synthetic-csrf-${csrfIssued}`,
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      },
    });
  });

  await page.route("**/api/v1/me/profile/password", async (route) => {
    passwordChangeRequests += 1;
    expect(route.request().method()).toBe("POST");
    expect(route.request().headers()["x-xsrf-token"]).toBe("synthetic-csrf-1");
    expect(Object.keys(route.request().postDataJSON()).sort()).toEqual([
      "currentPassword",
      "newPassword",
      "newPasswordConfirmation",
    ]);
    expect(route.request().postDataJSON()).toEqual({
      currentPassword: "synthetic-current-password",
      newPassword,
      newPasswordConfirmation: newPassword,
    });
    sessionActive = false;
    await route.fulfill({
      status: 200,
      json: {
        userId: identity.id,
        profileVersion: 9,
        reauthenticationRequired: true,
      },
    });
  });

  await page.route("**/api/v1/auth/login", async (route) => {
    loginRequests += 1;
    expect(route.request().headers()["x-xsrf-token"]).toBe("synthetic-csrf-2");
    expect(route.request().postDataJSON()).toEqual({
      identifier: "operador-prueba",
      password: newPassword,
    });
    sessionActive = true;
    await route.fulfill({ status: 204 });
  });

  await page.route("**/api/v1/auth/logout", async (route) => {
    logoutRequests += 1;
    sessionActive = false;
    await route.fulfill({ status: 204 });
  });

  await page.emulateMedia({ colorScheme: "light" });
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Inicio" })).toBeVisible();
  await page
    .getByRole("button", { name: "Menú de cuenta de Operador de prueba" })
    .click();
  await page.getByRole("link", { name: "Ajustes de mi cuenta" }).click();
  await expect(
    page.getByRole("heading", { name: "Ajustes de mi cuenta" }),
  ).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

  const themeToggle = page
    .locator(".cg-account-settings")
    .getByRole("button", { name: /Activar tema (claro|oscuro)/ });
  const initialThemeAction = await themeToggle.getAttribute("aria-label");
  await themeToggle.click();
  let updatedTheme =
    initialThemeAction === "Activar tema oscuro" ? "dark" : "light";
  await expect(page.locator("html")).toHaveAttribute(
    "data-theme",
    updatedTheme,
  );
  let themeActionAfterToggle = await themeToggle.getAttribute("aria-label");
  expect(themeActionAfterToggle).toBeTruthy();

  for (const width of [320, 390, 768, 1280]) {
    await page.setViewportSize({ width, height: 850 });
    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    expect(
      overflow,
      `Ajustes sin desbordamiento a ${width}px`,
    ).toBeLessThanOrEqual(1);
  }

  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.getByRole("link", { name: "Cambiar contraseña" }).click();
  await expect(
    page.getByRole("heading", { name: "Cambiar contraseña" }),
  ).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute(
    "data-theme",
    updatedTheme,
  );
  await expect(
    page.getByRole("button", { name: themeActionAfterToggle! }),
  ).toBeVisible();
  await expect(page.locator("img")).toHaveCount(0);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

  await page.getByRole("button", { name: "Activar tema claro" }).click();
  updatedTheme = "light";
  themeActionAfterToggle = "Activar tema oscuro";
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

  await page.getByLabel("Contraseña actual").fill("synthetic-current-password");
  await page.getByLabel("Nueva contraseña", { exact: true }).fill(newPassword);
  await page
    .getByLabel("Confirmar nueva contraseña", { exact: true })
    .fill(newPassword);
  await page.getByRole("button", { name: "Cambiar contraseña" }).click();

  await expect(
    page.getByRole("heading", { name: "Iniciar sesión" }),
  ).toBeVisible();
  await expect(
    page.getByText("Contraseña actualizada. Inicia sesión de nuevo."),
  ).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute(
    "data-theme",
    updatedTheme,
  );
  await expect(
    page.getByRole("button", { name: themeActionAfterToggle! }),
  ).toBeVisible();
  expect(passwordChangeRequests).toBe(1);
  expect(logoutRequests).toBe(0);
  expect(loginRequests).toBe(0);

  await page
    .getByRole("textbox", { name: "Usuario o correo" })
    .fill("operador-prueba");
  await page.getByLabel("Contraseña").fill(newPassword);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page.getByRole("heading", { name: "Inicio" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute(
    "data-theme",
    updatedTheme,
  );
  expect(passwordChangeRequests).toBe(1);
  expect(logoutRequests).toBe(0);
  expect(loginRequests).toBe(1);
  expect(csrfIssued).toBe(3);
});
