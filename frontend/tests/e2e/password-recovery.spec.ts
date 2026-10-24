import { expect, test } from "@playwright/test";

test("removes a recovery fragment before rendering and does not restore it after refresh", async ({
  page,
}) => {
  const token = "R".repeat(43);
  let completionRequests = 0;

  await page.route("**/api/v1/auth/me", async (route) => {
    await route.fulfill({
      status: 401,
      contentType: "application/problem+json",
      body: JSON.stringify({ status: 401, code: "AUTHENTICATION_REQUIRED" }),
    });
  });
  await page.route(
    "**/api/v1/auth/password-recovery/complete",
    async (route) => {
      completionRequests += 1;
      await route.fulfill({ status: 204 });
    },
  );

  await page.goto(`/recover-password#token=${token}`);

  await expect(
    page.getByRole("heading", { name: "Define una contraseña nueva" }),
  ).toBeVisible();
  await expect
    .poll(() => page.url())
    .toBe("http://127.0.0.1:5173/recover-password");
  await expect(page.locator("body")).not.toContainText(token);

  await page.reload();

  await expect(
    page.getByText(
      "El enlace no está disponible. Abre el enlace completo del correo o solicita uno nuevo.",
    ),
  ).toBeVisible();
  await expect(page.locator("body")).not.toContainText(token);
  expect(completionRequests).toBe(0);
});
