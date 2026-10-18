import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

const identity = {
  id: "00000000-0000-4000-8000-000000000003",
  username: "operador-prueba",
  fullName: "Operador de prueba de sucursales regionales",
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

test("requires a server session, logs in once, and explicitly logs out", async ({
  page,
}) => {
  let sessionActive = false;
  let csrfIssued = 0;
  let identityRequests = 0;
  let branchContextRequests = 0;
  let profileRequests = 0;
  let holdIdentityRead = false;
  let loginRequests = 0;
  let logoutRequests = 0;
  let releaseManualIdentityRead: (() => void) | undefined;
  let notifyManualIdentityReadStarted: (() => void) | undefined;
  const manualIdentityReadStarted = new Promise<void>((resolve) => {
    notifyManualIdentityReadStarted = resolve;
  });
  const manualIdentityReadGate = new Promise<void>((resolve) => {
    releaseManualIdentityRead = resolve;
  });

  await page.route("**/api/v1/auth/me", async (route) => {
    identityRequests += 1;
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
    if (holdIdentityRead) {
      holdIdentityRead = false;
      notifyManualIdentityReadStarted?.();
      await manualIdentityReadGate;
    }
    await route.fulfill({ status: 200, json: identity });
  });

  await page.route("**/api/v1/me/branch-context", async (route) => {
    branchContextRequests += 1;
    await route.fulfill({
      status: 500,
      body: "Unexpected branch-context request",
    });
  });
  await page.route("**/api/v1/me/profile/**", async (route) => {
    profileRequests += 1;
    await route.fulfill({ status: 500, body: "Unexpected profile request" });
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

  await page.route("**/api/v1/auth/login", async (route) => {
    loginRequests += 1;
    const request = route.request();
    expect(request.method()).toBe("POST");
    expect(request.headers()["x-xsrf-token"]).toBe("synthetic-csrf-1");
    expect(request.postDataJSON()).toEqual({
      identifier: "staff@example.test",
      password: "synthetic-password",
    });
    sessionActive = true;
    await route.fulfill({ status: 204 });
  });

  await page.route("**/api/v1/auth/logout", async (route) => {
    logoutRequests += 1;
    expect(route.request().method()).toBe("POST");
    expect(route.request().headers()["x-xsrf-token"]).toBe("synthetic-csrf-3");
    sessionActive = false;
    await route.fulfill({ status: 204 });
  });

  await page.goto("/");
  await expect(page).toHaveTitle("Iniciar sesión · Coach Gym");
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Activar tema oscuro" }),
  ).toBeVisible();

  const loginAccessibility = await new AxeBuilder({ page }).analyze();
  expect(loginAccessibility.violations).toEqual([]);

  await page
    .getByRole("textbox", { name: "Usuario o correo" })
    .fill("staff@example.test");
  await page.getByLabel("Contraseña").fill("synthetic-password");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();

  await expect(
    page.getByRole("heading", { level: 1, name: "Inicio" }),
  ).toBeVisible();
  await expect(page.locator('input[type="password"]')).toHaveCount(0);
  const accountTrigger = page.getByRole("button", {
    name: "Menú de cuenta de Operador de prueba",
  });
  await expect(accountTrigger).toContainText("Operador de prueba");
  expect(
    await accountTrigger
      .locator(".cg-account-avatar")
      .evaluate((avatar) => getComputedStyle(avatar, "::after").content),
  ).toBe('"OR"');
  await expect(
    page.getByRole("group", {
      name: "Ámbito de organización · Sin sucursal activa",
    }),
  ).toBeVisible();
  await expect(page.getByText("Sucursal de prueba")).toBeVisible();
  expect(loginRequests).toBe(1);
  expect(csrfIssued).toBe(2);

  const privateAccessibility = await new AxeBuilder({ page }).analyze();
  expect(privateAccessibility.violations).toEqual([]);

  await accountTrigger.focus();
  await page.keyboard.press("Enter");
  const logoutItem = page.getByRole("menuitem", { name: "Cerrar sesión" });
  await expect(logoutItem).toBeVisible();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.keyboard.press("Escape");
  await expect(logoutItem).toBeHidden();
  await expect(accountTrigger).toBeFocused();

  const readsBeforeRefresh = identityRequests;
  holdIdentityRead = true;
  await page.getByRole("button", { name: "Actualizar contexto" }).click();
  await manualIdentityReadStarted;
  await expect(
    page.getByRole("heading", { name: "Comprobando la sesión" }),
  ).toBeVisible();
  await expect(page.getByText("Operador de prueba")).toHaveCount(0);
  releaseManualIdentityRead?.();
  await expect(
    page.getByRole("heading", { level: 1, name: "Inicio" }),
  ).toBeVisible();
  expect(identityRequests).toBe(readsBeforeRefresh + 1);
  expect(branchContextRequests).toBe(0);
  expect(profileRequests).toBe(0);

  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 850 });
    await expect(accountTrigger).toBeVisible();
    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    );
    const overflowDetails = await page.evaluate(() =>
      Array.from(document.body.querySelectorAll<HTMLElement>("*"))
        .map((element) => {
          const rect = element.getBoundingClientRect();
          return {
            tag: element.tagName,
            className: String(element.className),
            text: element.innerText?.slice(0, 40),
            left: Math.round(rect.left),
            right: Math.round(rect.right),
            width: Math.round(rect.width),
            scrollWidth: element.scrollWidth,
            cssWidth: getComputedStyle(element).width,
            cssMaxWidth: getComputedStyle(element).maxWidth,
            cssMinWidth: getComputedStyle(element).minWidth,
            flex: getComputedStyle(element).flex,
          };
        })
        .filter(
          ({ left, right, scrollWidth }) =>
            left < -1 ||
            right > window.innerWidth + 1 ||
            scrollWidth > window.innerWidth,
        ),
    );
    expect(
      overflow,
      `No debe haber desbordamiento a ${width}px: ${JSON.stringify(overflowDetails)}`,
    ).toBeLessThanOrEqual(1);
  }
  await page.setViewportSize({ width: 320, height: 850 });
  await accountTrigger.focus();
  await page.keyboard.press("Enter");
  await expect(logoutItem).toBeVisible();
  const menuOverflow = await page.evaluate(
    () =>
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth,
  );
  expect(menuOverflow).toBeLessThanOrEqual(1);
  await page.keyboard.press("Escape");
  await expect(accountTrigger).toBeFocused();
  await page.setViewportSize({ width: 1280, height: 850 });

  await page.getByRole("button", { name: "Activar tema oscuro" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await accountTrigger.focus();
  await page.keyboard.press("Enter");
  await expect(logoutItem).toBeVisible();
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await logoutItem.click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await expect(page.getByText("Sesión cerrada.")).toBeVisible();
  expect(logoutRequests).toBe(1);
  expect(sessionActive).toBe(false);
  expect(branchContextRequests).toBe(0);
  expect(profileRequests).toBe(0);
  expect(csrfIssued).toBe(3);
});

test("completes the required password change and returns to fresh login", async ({
  page,
}) => {
  let sessionActive = false;
  let identityRequests = 0;
  let identityRequestsAtPasswordChange: number | undefined;
  let csrfIssued = 0;
  let passwordChangeRequests = 0;

  await page.route("**/api/v1/auth/me", async (route) => {
    identityRequests += 1;
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
    await route.fulfill({
      status: 200,
      json: { ...identity, passwordChangeRequired: true },
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

  await page.route("**/api/v1/auth/login", async (route) => {
    expect(route.request().headers()["x-xsrf-token"]).toBe("synthetic-csrf-1");
    sessionActive = true;
    await route.fulfill({ status: 204 });
  });

  await page.route("**/api/v1/me/profile/password", async (route) => {
    passwordChangeRequests += 1;
    identityRequestsAtPasswordChange = identityRequests;
    const request = route.request();
    expect(request.method()).toBe("POST");
    expect(request.headers()["x-xsrf-token"]).toBe(
      `synthetic-csrf-${passwordChangeRequests + 2}`,
    );
    expect(Object.keys(request.postDataJSON()).sort()).toEqual([
      "currentPassword",
      "newPassword",
      "newPasswordConfirmation",
    ]);
    const currentPassword =
      passwordChangeRequests === 1
        ? "synthetic-wrong-current"
        : "synthetic-current";
    expect(request.postDataJSON()).toEqual({
      currentPassword,
      newPassword: "synthetic-new-password",
      newPasswordConfirmation: "synthetic-new-password",
    });
    if (passwordChangeRequests === 1) {
      await route.fulfill({
        status: 400,
        contentType: "application/problem+json",
        body: JSON.stringify({
          status: 400,
          code: "CURRENT_PASSWORD_INVALID",
        }),
      });
      return;
    }
    sessionActive = false;
    await route.fulfill({
      status: 200,
      json: {
        userId: identity.id,
        profileVersion: 13,
        reauthenticationRequired: true,
      },
    });
  });

  await page.goto("/");
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await page.getByRole("textbox", { name: "Usuario o correo" }).fill("staff");
  await page.getByLabel("Contraseña").fill("synthetic-login");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();

  await expect(
    page.getByRole("heading", {
      level: 1,
      name: "Actualiza tu contraseña",
    }),
  ).toBeVisible();
  await expect(page.getByLabel("Contraseña actual")).toBeVisible();
  await page.getByRole("button", { name: "Activar tema oscuro" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expect(
    page.getByRole("button", { name: "Activar tema claro" }),
  ).toBeVisible();

  await page.getByLabel("Contraseña actual").focus();
  await page.keyboard.press("Tab");
  await expect(
    page.getByRole("button", { name: "Mostrar" }).first(),
  ).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(
    page.getByLabel("Nueva contraseña", { exact: true }),
  ).toBeFocused();

  const passwordAccessibility = await new AxeBuilder({ page }).analyze();
  expect(passwordAccessibility.violations).toEqual([]);

  async function fillPasswordChange(currentPassword: string): Promise<void> {
    await page.getByLabel("Contraseña actual").fill(currentPassword);
    await page
      .getByLabel("Nueva contraseña", { exact: true })
      .fill("synthetic-new-password");
    await page
      .getByLabel("Confirmar nueva contraseña", { exact: true })
      .fill("synthetic-new-password");
  }

  await fillPasswordChange("synthetic-wrong-current");
  await page.getByRole("button", { name: "Actualizar contraseña" }).click();
  await expect(
    page
      .getByRole("alert")
      .getByText(
        "No se pudo verificar la contraseña actual. Revísala e inténtalo de nuevo.",
      ),
  ).toBeVisible();
  await expect(page.getByLabel("Contraseña actual")).toHaveValue("");
  await expect(
    page.getByLabel("Nueva contraseña", { exact: true }),
  ).toHaveValue("");
  await expect(
    page.getByLabel("Confirmar nueva contraseña", { exact: true }),
  ).toHaveValue("");

  await fillPasswordChange("synthetic-current");
  await page.getByRole("button", { name: "Actualizar contraseña" }).dblclick();

  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await expect(
    page.getByText("Contraseña actualizada. Inicia sesión de nuevo."),
  ).toBeVisible();
  await expect(page.getByLabel("Contraseña actual")).toHaveCount(0);
  expect(identityRequestsAtPasswordChange).toBeGreaterThanOrEqual(2);
  expect(identityRequests).toBe(identityRequestsAtPasswordChange);
  expect(passwordChangeRequests).toBe(2);
  expect(csrfIssued).toBe(4);
});
