import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

test("reviews and accepts an invitation from a scrubbed direct link", async ({
  page,
}) => {
  const token = "I".repeat(43);
  let inspectRequests = 0;
  let acceptRequests = 0;
  let loginRequests = 0;
  const branchId = "22222222-2222-4222-8222-222222222222";

  await page.route("**/api/v1/auth/me", async (route) => {
    await route.fulfill({
      status: 401,
      contentType: "application/problem+json",
      body: JSON.stringify({ status: 401, code: "AUTHENTICATION_REQUIRED" }),
    });
  });
  await page.route("**/api/v1/auth/login", async (route) => {
    loginRequests += 1;
    await route.fulfill({
      status: 401,
      contentType: "application/problem+json",
      body: JSON.stringify({ status: 401, code: "INVALID_CREDENTIALS" }),
    });
  });
  await page.route("**/api/v1/auth/csrf", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        token: "synthetic-csrf-token",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      }),
    });
  });
  await page.route("**/api/v1/staff-invitations/inspect", async (route) => {
    inspectRequests += 1;
    expect(route.request().headers()["x-xsrf-token"]).toBe(
      "synthetic-csrf-token",
    );
    expect(route.request().postDataJSON()).toEqual({ token });
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        invitationId: "11111111-1111-4111-8111-111111111111",
        maskedEmail: "g***@example.test",
        proposedRole: "RECEPTIONIST",
        proposedScope: "BRANCH",
        proposedBranches: [
          { branchId, code: "CENTRAL", name: "Sucursal central" },
        ],
        expiresAt: "2030-02-03T12:30:00Z",
      }),
    });
  });
  await page.route("**/api/v1/staff-invitations/accept", async (route) => {
    acceptRequests += 1;
    expect(route.request().headers()["x-xsrf-token"]).toBe(
      "synthetic-csrf-token",
    );
    expect(route.request().postDataJSON()).toEqual({
      token,
      firstName: "Ana",
      lastName: "Prueba",
      password: "synthetic invitation password",
      passwordConfirmation: "synthetic invitation password",
    });
    await route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        userId: "33333333-3333-4333-8333-333333333333",
        role: "RECEPTIONIST",
        scope: "BRANCH",
        branchIds: [branchId],
        acceptedAt: "2030-02-03T12:30:00Z",
      }),
    });
  });

  await page.goto(`/accept-invitation#token=${token}`);
  await expect(
    page.getByRole("heading", { name: "Aceptar invitación" }),
  ).toBeVisible();
  await expect
    .poll(() => page.url())
    .toBe("http://127.0.0.1:5173/accept-invitation");
  expect(inspectRequests).toBe(0);
  expect(acceptRequests).toBe(0);
  await expect(page.locator("body")).not.toContainText(token);

  await page.getByRole("button", { name: "Revisar invitación" }).click();
  await expect(page.getByText("g***@example.test")).toBeVisible();
  await expect(page.getByText("Sucursal central")).toBeVisible();
  await page.setViewportSize({ width: 320, height: 700 });
  const horizontalOverflow = await page.evaluate(
    () =>
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth,
  );
  expect(horizontalOverflow).toBeLessThanOrEqual(1);
  const accessibility = await new AxeBuilder({ page }).analyze();
  expect(accessibility.violations).toEqual([]);
  await expect.poll(() => inspectRequests).toBe(1);
  expect(acceptRequests).toBe(0);

  await page.getByLabel("Nombre").fill("Ana");
  await page.getByLabel("Apellido").fill("Prueba");
  await page
    .getByLabel("Contraseña", { exact: true })
    .fill("synthetic invitation password");
  await page
    .getByLabel("Confirma la contraseña")
    .fill("synthetic invitation password");
  await page.getByRole("button", { name: "Aceptar invitación" }).click();

  await expect(
    page.getByRole("heading", { name: "Invitación aceptada" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Ir a iniciar sesión" }),
  ).toHaveAttribute("href", "/login");
  await expect.poll(() => acceptRequests).toBe(1);
  expect(loginRequests).toBe(0);
  await expect(page.locator("body")).not.toContainText(token);
  await expect(page.locator("body")).not.toContainText(
    "33333333-3333-4333-8333-333333333333",
  );
  await expect(page.locator("body")).not.toContainText(
    "synthetic invitation password",
  );
  expect(
    await page.evaluate(() =>
      JSON.stringify({ ...localStorage, ...sessionStorage }),
    ),
  ).not.toContain(token);
  expect(
    await page.evaluate(() =>
      JSON.stringify({ ...localStorage, ...sessionStorage }),
    ),
  ).not.toContain("synthetic invitation password");
});

test("clears an invitation token on exit and does not restore it after navigation", async ({
  page,
}) => {
  const token = "I".repeat(43);
  let inspectRequests = 0;

  await page.route("**/api/v1/auth/me", async (route) => {
    await route.fulfill({
      status: 401,
      contentType: "application/problem+json",
      body: JSON.stringify({ status: 401, code: "AUTHENTICATION_REQUIRED" }),
    });
  });
  await page.route("**/api/v1/staff-invitations/inspect", async (route) => {
    inspectRequests += 1;
    await route.fulfill({ status: 409 });
  });

  await page.goto(`/accept-invitation#token=${token}`);
  await expect(
    page.getByRole("button", { name: "Revisar invitación" }),
  ).toBeVisible();
  await expect
    .poll(() => page.url())
    .toBe("http://127.0.0.1:5173/accept-invitation");
  await page
    .getByRole("link", { name: "Cancelar y volver a iniciar sesión" })
    .click();
  await expect(
    page.getByRole("heading", { name: "Iniciar sesión" }),
  ).toBeVisible();

  await page.goBack();
  await expect(
    page.getByText(
      "El enlace de invitación no está disponible. Abre el enlace completo que recibiste o solicita a administración una invitación nueva.",
    ),
  ).toBeVisible();
  expect(inspectRequests).toBe(0);
  await expect(page.locator("body")).not.toContainText(token);
});
