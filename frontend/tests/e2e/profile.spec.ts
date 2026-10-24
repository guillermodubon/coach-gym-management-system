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

test("views and updates the current staff profile using only editable names", async ({
  page,
}) => {
  let currentProfile = {
    userId: identity.id,
    username: identity.username,
    email: "operator@example.test",
    firstName: "Ana",
    lastName: "Ríos",
    displayName: "Ana Ríos",
    roles: ["ADMIN"],
    status: "ACTIVE",
    photoPresent: false,
    photoUrl: null,
    version: 4,
  };
  let currentIdentity = { ...identity };
  let profileReads = 0;
  let photoReads = 0;
  let updateCount = 0;

  await page.route("**/api/v1/auth/me", async (route) => {
    await route.fulfill({ status: 200, json: currentIdentity });
  });
  await page.route("**/api/v1/me/branch-context", async (route) => {
    await route.fulfill({
      status: 200,
      json: {
        organizationId: "00000000-0000-4000-8000-000000000001",
        scopeType: "ORGANIZATION",
        activeBranch: null,
        availableBranches: identity.availableBranches,
      },
    });
  });
  await page.route("**/api/v1/auth/csrf", async (route) => {
    await route.fulfill({
      status: 200,
      json: {
        token: "synthetic-csrf-profile",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      },
    });
  });
  await page.route("**/api/v1/me/profile/photo", async (route) => {
    photoReads += 1;
    await route.fulfill({
      status: 500,
      body: "No photo is present in this profile",
    });
  });
  await page.route("**/api/v1/me/profile", async (route) => {
    const request = route.request();
    if (request.method() === "GET") {
      profileReads += 1;
      await route.fulfill({ status: 200, json: currentProfile });
      return;
    }

    updateCount += 1;
    expect(request.method()).toBe("PUT");
    expect(request.headers()["x-xsrf-token"]).toBe("synthetic-csrf-profile");
    expect(request.postDataJSON()).toEqual({
      firstName: "Ana María",
      lastName: "Ríos",
      version: 4,
    });
    currentProfile = {
      ...currentProfile,
      firstName: "Ana María",
      displayName: "Ana María Ríos",
      version: 5,
    };
    currentIdentity = {
      ...currentIdentity,
      fullName: currentProfile.displayName,
    };
    await route.fulfill({ status: 200, json: currentProfile });
  });

  await page.goto("/mi-perfil");
  await expect(page).toHaveTitle("Mi perfil · Coach Gym");
  await expect(page.getByLabel("Nombre")).toHaveValue("Ana");
  await expect(page.getByLabel("Apellido")).toHaveValue("Ríos");
  await expect(page.getByText("operator@example.test")).toBeVisible();
  await expect(page.getByText("operador-prueba")).toBeVisible();

  const accessibility = await new AxeBuilder({ page }).analyze();
  expect(accessibility.violations).toEqual([]);

  await page.getByLabel("Nombre").fill(" Ana   María ");
  await page.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(
    page.getByRole("button", { name: "Menú de cuenta de Ana María Ríos" }),
  ).toBeVisible();
  await expect(page.getByLabel("Nombre")).toHaveValue("Ana María");
  expect(updateCount).toBe(1);
  expect(profileReads).toBeGreaterThanOrEqual(1);
  expect(photoReads).toBe(0);
});
