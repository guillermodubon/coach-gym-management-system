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

const photoPath = "/api/v1/me/profile/photo";
const png = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/c6UAAAAASUVORK5CYII=",
  "base64",
);

test("uploads and removes the authenticated user's private profile photo", async ({
  page,
}) => {
  let profile = {
    userId: identity.id,
    username: identity.username,
    email: "operator@example.test",
    firstName: "Ana",
    lastName: "Ríos",
    displayName: "Ana Ríos",
    roles: ["ADMIN"],
    status: "ACTIVE",
    photoPresent: false,
    photoUrl: null as string | null,
    version: 4,
  };
  let profileReads = 0;
  let photoReads = 0;
  let uploads = 0;
  let removals = 0;

  await page.route("**/api/v1/auth/me", (route) =>
    route.fulfill({ status: 200, json: identity }),
  );
  await page.route("**/api/v1/me/branch-context", (route) =>
    route.fulfill({
      status: 200,
      json: {
        organizationId: "00000000-0000-4000-8000-000000000001",
        scopeType: "ORGANIZATION",
        activeBranch: null,
        availableBranches: identity.availableBranches,
      },
    }),
  );
  await page.route("**/api/v1/auth/csrf", (route) =>
    route.fulfill({
      status: 200,
      json: {
        token: "synthetic-csrf-photo",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      },
    }),
  );
  await page.route("**/api/v1/me/profile/photo**", async (route) => {
    const request = route.request();
    if (request.method() === "GET") {
      photoReads += 1;
      await route.fulfill({
        status: 200,
        body: png,
        headers: {
          "Cache-Control": "private, no-store",
          "Content-Type": "image/png",
          "X-Content-Type-Options": "nosniff",
        },
      });
      return;
    }

    expect(request.headers()["x-xsrf-token"]).toBe("synthetic-csrf-photo");
    if (request.method() === "PUT") {
      uploads += 1;
      const body = request.postData() ?? "";
      expect(body).toContain('name="version"');
      expect(body).toMatch(/\r\n\r\n4\r\n/);
      expect(body).toContain('filename="profile-photo"');
      profile = {
        ...profile,
        photoPresent: true,
        photoUrl: photoPath,
        version: 5,
      };
      await route.fulfill({
        status: 200,
        json: {
          photoId: "00000000-0000-4000-8000-000000000004",
          contentType: "image/png",
          sizeBytes: png.length,
          updatedAt: "2026-10-22T12:00:00Z",
          version: 5,
        },
      });
      return;
    }

    expect(request.method()).toBe("DELETE");
    expect(new URL(request.url()).search).toBe("?version=5");
    expect(request.postData()).toBeNull();
    removals += 1;
    profile = { ...profile, photoPresent: false, photoUrl: null, version: 6 };
    await route.fulfill({ status: 200, json: profile });
  });
  await page.route("**/api/v1/me/profile", async (route) => {
    if (route.request().method() !== "GET") {
      await route.fulfill({ status: 405 });
      return;
    }
    profileReads += 1;
    await route.fulfill({ status: 200, json: profile });
  });

  await page.goto("/mi-perfil");
  await expect(page.getByRole("heading", { name: "Mi perfil" })).toBeVisible();
  await expect(page.getByLabel("Nombre")).toHaveValue("Ana");
  expect(photoReads).toBe(0);

  const chooseFile = async () => {
    await page.locator('input[type="file"]').setInputFiles({
      name: "synthetic.png",
      mimeType: "image/png",
      buffer: png,
    });
  };

  await chooseFile();
  await expect(
    page.getByText(/Imagen seleccionada: synthetic.png/),
  ).toBeVisible();
  await expect(page.locator(".cg-profile-avatar img")).toHaveAttribute(
    "src",
    /^blob:/,
  );
  expect(uploads).toBe(0);
  await page.getByRole("button", { name: "Cancelar selección" }).click();
  await expect(page.locator(".cg-profile-avatar img")).toHaveCount(0);

  await chooseFile();
  await page.getByRole("button", { name: "Guardar foto" }).click();
  await expect(page.locator(".cg-account-avatar img")).toHaveAttribute(
    "src",
    /^blob:/,
  );
  await expect(page.locator(".cg-profile-avatar img")).toHaveAttribute(
    "src",
    /^blob:/,
  );
  expect(uploads).toBe(1);
  expect(profileReads).toBeGreaterThanOrEqual(2);
  expect(photoReads).toBe(1);

  await page.getByRole("button", { name: "Eliminar foto" }).click();
  const dialog = page.getByRole("dialog", { name: "Eliminar foto de perfil" });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("button", { name: "Eliminar foto" }).click();
  await expect(page.locator(".cg-account-avatar img")).toHaveCount(0);
  await expect(page.locator(".cg-profile-avatar img")).toHaveCount(0);
  expect(removals).toBe(1);
  expect(photoReads).toBe(1);
  await expect(page.locator(".cg-account-avatar")).toHaveAttribute(
    "data-initials",
    "OP",
  );
});
