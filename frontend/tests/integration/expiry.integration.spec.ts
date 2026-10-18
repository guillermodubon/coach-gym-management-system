import { expect, test, createStaffFixture } from "./fixtures";

test("R13 the server expires an idle session under the isolated PT15S policy", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "BRANCH",
    branchAssignments: 1,
  });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await page
    .getByRole("textbox", { name: "Usuario o correo" })
    .fill(account.username);
  await page.getByLabel("Contraseña").fill(account.password);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Inicio" }),
  ).toBeVisible();

  // This deliberate wait is the backend policy under test, not a render guess.
  await page.waitForTimeout(16_000);
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await expect(page.getByText(account.fullName, { exact: true })).toHaveCount(
    0,
  );
  const result = await page.evaluate(async () => {
    const response = await fetch("/api/v1/auth/me", {
      credentials: "same-origin",
      cache: "no-store",
    });
    return response.status;
  });
  expect(result).toBe(401);
});
