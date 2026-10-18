import { randomBytes, randomUUID } from "node:crypto";
import AxeBuilder from "@axe-core/playwright";
import type { Page } from "@playwright/test";
import { waitForVisualTransitions } from "../helpers/visual-stability";
import {
  createStaffFixture,
  invalidateFixtureSecurityVersion,
  expect,
  test,
  type StaffFixture,
} from "./fixtures";

interface CurrentUserResponse {
  username: string;
  fullName: string;
  roles: string[];
  organizationScope: "ORGANIZATION" | "BRANCH";
  activeBranch: { id: string; code: string; name: string } | null;
  availableBranches: Array<{ id: string; code: string; name: string }>;
  passwordChangeRequired: boolean;
}

async function readCurrentUser(page: Page) {
  return page.evaluate(async () => {
    const response = await fetch("/api/v1/auth/me", {
      credentials: "same-origin",
      cache: "no-store",
    });
    if (response.status !== 200) return { status: response.status, user: null };
    const value = (await response.json()) as CurrentUserResponse;
    return {
      status: response.status,
      user: {
        username: value.username,
        fullName: value.fullName,
        roles: value.roles,
        organizationScope: value.organizationScope,
        activeBranch: value.activeBranch,
        availableBranches: value.availableBranches,
        passwordChangeRequired: value.passwordChangeRequired,
      },
    };
  });
}

async function signIn(page: Page, account: StaffFixture): Promise<void> {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await page
    .getByRole("textbox", { name: "Usuario o correo" })
    .fill(account.username);
  await page.getByLabel("Contraseña").fill(account.password);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
}

async function expectHome(page: Page): Promise<void> {
  await expect(
    page.getByRole("heading", { level: 1, name: "Inicio" }),
  ).toBeVisible();
}

async function openAccountMenu(
  page: Page,
  account: StaffFixture,
): Promise<void> {
  const trigger = page.getByRole("button", {
    name: `Menú de cuenta de ${account.fullName}`,
  });
  await expect(trigger).toBeVisible();
  await trigger.focus();
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("menuitem", { name: "Cerrar sesión" }),
  ).toBeVisible();
}

async function signOutThroughUi(
  page: Page,
  account: StaffFixture,
): Promise<void> {
  await openAccountMenu(page, account);
  await page.getByRole("menuitem", { name: "Cerrar sesión" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
}

async function expectNoHorizontalOverflow(
  page: Page,
  check: "width-390" | "width-320" | "width-320-menu",
): Promise<void> {
  const overflow = await page.evaluate(
    () =>
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth,
  );
  const overflowSources =
    overflow > 1
      ? await page.evaluate(() => {
          const candidates = [
            ["cg-app-layout", ".cg-app-layout"],
            ["cg-sidebar", ".cg-sidebar"],
            ["cg-content-layout", ".cg-content-layout"],
            ["cg-header", ".cg-header"],
            ["cg-header-leading", ".cg-header-leading"],
            ["cg-header-actions", ".cg-header-actions"],
            ["cg-header-controls", ".cg-header-controls"],
            ["cg-header-context", ".cg-header-context"],
            ["cg-header-account", ".cg-header-account"],
            ["cg-account-trigger", ".cg-account-trigger"],
            ["cg-mobile-drawer", ".cg-mobile-drawer"],
            ["ant-drawer", ".ant-drawer"],
            ["ant-dropdown", ".ant-dropdown"],
            ["cg-home-welcome", ".cg-home-welcome"],
            ["cg-main-content", ".cg-main-content"],
            ["root", "#root"],
            ["body", "body"],
          ] as const;
          return candidates.flatMap(([name, selector]) => {
            const element = document.querySelector<HTMLElement>(selector);
            if (!element) return [];
            const rect = element.getBoundingClientRect();
            return rect.right > window.innerWidth + 1 ||
              element.scrollWidth > element.clientWidth + 1
              ? [name]
              : [];
          });
        })
      : [];
  const sourceMarker = overflowSources.join("+");
  expect(
    overflow,
    `responsive-overflow:${check}:${overflow}:${sourceMarker}`,
  ).toBeLessThanOrEqual(1);
}

async function expectNoAxeViolations(page: Page): Promise<void> {
  await waitForVisualTransitions(page);
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.violations.map((violation) => violation.id)).toEqual([]);
}

function countRequests(page: Page, pathname: string): () => number {
  let count = 0;
  page.on("request", (request) => {
    try {
      if (new URL(request.url()).pathname === pathname) count += 1;
    } catch {
      // Request URLs are not logged by the integration suite.
    }
  });
  return () => count;
}

test("R01 anonymous session and direct root route remain private", async ({
  page,
}) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { level: 1, name: "Inicio" }),
  ).toHaveCount(0);
  expect((await readCurrentUser(page)).status).toBe(401);
  await page.reload();
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await expectNoAxeViolations(page);
});

test("R02 real login covers the supported role and scope combinations", async ({
  page,
  database,
}) => {
  const longName = {
    firstName: "Operador de prueba con nombre de sucursales",
    lastName: "regionales",
  };
  const organizationAdmin = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
    ...longName,
  });
  const branchAdmin = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "BRANCH",
    branchAssignments: 1,
  });
  const receptionist = await createStaffFixture(database, {
    role: "RECEPTIONIST",
    scope: "BRANCH",
    branchAssignments: 2,
  });

  await page.goto("/");
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await expectNoAxeViolations(page);

  await signIn(page, organizationAdmin);
  await expectHome(page);
  let current = await readCurrentUser(page);
  expect(current.status).toBe(200);
  expect(current.user?.roles).toEqual(["ADMIN"]);
  expect(current.user?.organizationScope).toBe("ORGANIZATION");
  expect(current.user?.activeBranch).toBeNull();
  expect(current.user?.availableBranches).toHaveLength(2);
  await openAccountMenu(page, organizationAdmin);
  await expect(page.getByRole("menu").last()).toContainText(
    organizationAdmin.fullName,
  );
  await expect(page.getByRole("menu").last()).toContainText(
    "Administrador · Organización",
  );
  await expectNoAxeViolations(page);
  await page.keyboard.press("Escape");
  await signOutThroughUi(page, organizationAdmin);

  await signIn(page, branchAdmin);
  await expectHome(page);
  current = await readCurrentUser(page);
  expect(current.status).toBe(200);
  expect(current.user?.roles).toEqual(["ADMIN"]);
  expect(current.user?.organizationScope).toBe("BRANCH");
  expect(current.user?.activeBranch?.code).toBe("PRINCIPAL");
  await openAccountMenu(page, branchAdmin);
  await expect(page.getByRole("menu").last()).toContainText(
    "Administrador · Sucursales asignadas",
  );
  await page.keyboard.press("Escape");
  await signOutThroughUi(page, branchAdmin);

  await signIn(page, receptionist);
  await expectHome(page);
  current = await readCurrentUser(page);
  expect(current.status).toBe(200);
  expect(current.user?.roles).toEqual(["RECEPTIONIST"]);
  expect(current.user?.organizationScope).toBe("BRANCH");
  expect(current.user?.activeBranch).toBeNull();
  expect(current.user?.availableBranches).toHaveLength(2);
  await openAccountMenu(page, receptionist);
  await expect(page.getByRole("menu").last()).toContainText(
    "Recepción · Sucursales asignadas",
  );
  await page.keyboard.press("Escape");
  await expect(page.locator(".ant-dropdown")).toHaveCount(0);
  await expect(
    page.getByRole("button", {
      name: `Menú de cuenta de ${receptionist.fullName}`,
    }),
  ).toBeFocused();

  await page.setViewportSize({ width: 390, height: 850 });
  await expectNoHorizontalOverflow(page, "width-390");
  await page.setViewportSize({ width: 320, height: 850 });
  await expectNoHorizontalOverflow(page, "width-320");
  await page.setViewportSize({ width: 320, height: 850 });
  await openAccountMenu(page, receptionist);
  await expectNoHorizontalOverflow(page, "width-320-menu");
  await page.keyboard.press("Escape");
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.getByRole("button", { name: "Activar tema oscuro" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expectNoAxeViolations(page);
  await page.getByRole("button", { name: "Activar tema claro" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await expectNoAxeViolations(page);
});

test("R03 invalid credentials stay generic and do not establish a session", async ({
  page,
}) => {
  const loginRequests = countRequests(page, "/api/v1/auth/login");
  await page.goto("/login");
  await page
    .getByRole("textbox", { name: "Usuario o correo" })
    .fill(`missing-${randomUUID()}`);
  await page.getByLabel("Contraseña").fill(randomUUID());
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  expect(await page.getByLabel("Contraseña").inputValue()).toBe("");
  expect(loginRequests()).toBe(1);
  expect((await readCurrentUser(page)).status).toBe(401);
});

test("R04 reload restores identity only from the server session cookie", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  await signIn(page, account);
  await expectHome(page);

  const cookies = await page.context().cookies("http://127.0.0.1:5183");
  const sessionCookie = cookies.find((cookie) => cookie.name === "JSESSIONID");
  const csrfCookie = cookies.find((cookie) => cookie.name === "XSRF-TOKEN");
  expect(sessionCookie?.httpOnly ?? false).toBe(true);
  expect(sessionCookie?.secure ?? true).toBe(false);
  expect(sessionCookie?.sameSite).toBe("Lax");
  expect(sessionCookie?.path).toBe("/");
  expect(csrfCookie?.httpOnly ?? true).toBe(false);
  expect(csrfCookie?.sameSite).toBe("Lax");

  await page.reload();
  await expectHome(page);
  const user = await readCurrentUser(page);
  expect(user.status).toBe(200);
  expect(user.user?.username).toBe(account.username);
  const hasPersistedCredentials = await page.evaluate(
    ({ username, password }) => {
      const values = [
        ...Object.values(localStorage),
        ...Object.values(sessionStorage),
      ];
      return values.some(
        (value) => value.includes(username) || value.includes(password),
      );
    },
    { username: account.username, password: account.password },
  );
  expect(hasPersistedCredentials).toBe(false);
});

test("R05 logout enforces real CSRF and succeeds through the session controller", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  let logoutHasCsrfHeader = false;
  page.on("request", (request) => {
    if (
      request.method() === "POST" &&
      new URL(request.url()).pathname === "/api/v1/auth/logout"
    ) {
      logoutHasCsrfHeader = Boolean(request.headers()["x-xsrf-token"]);
    }
  });
  await signIn(page, account);
  await expectHome(page);

  const rejectedStatus = await page.evaluate(async () => {
    const response = await fetch("/api/v1/auth/logout", {
      method: "POST",
      credentials: "same-origin",
      cache: "no-store",
    });
    return response.status;
  });
  expect(rejectedStatus).toBe(403);
  expect((await readCurrentUser(page)).status).toBe(200);

  await signOutThroughUi(page, account);
  expect(logoutHasCsrfHeader).toBe(true);
  expect((await readCurrentUser(page)).status).toBe(401);
});

test("R06 confirmed logout and browser history never restore private content", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  await signIn(page, account);
  await expectHome(page);
  await page.goto("/login");
  await expectHome(page);
  await signOutThroughUi(page, account);
  await page.goBack();
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await expect(page.getByText(account.fullName, { exact: true })).toHaveCount(
    0,
  );
  expect((await readCurrentUser(page)).status).toBe(401);
});

test("R07 required password change invalidates the old session and requires a fresh login", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
    passwordChangeRequired: true,
  });
  await page.goto("/cambio-contrasena-obligatorio");
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await page
    .getByRole("textbox", { name: "Usuario o correo" })
    .fill(account.username);
  await page.getByLabel("Contraseña").fill(account.password);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Actualiza tu contraseña" }),
  ).toBeVisible();
  await expect(page).toHaveURL(/\/cambio-contrasena-obligatorio$/);
  await expect(page.getByText("Contexto operativo")).toHaveCount(0);

  const replacement = randomBytes(24).toString("base64url");
  await page.getByLabel("Contraseña actual").fill(account.password);
  await page.getByLabel("Nueva contraseña", { exact: true }).fill(replacement);
  await page
    .getByLabel("Confirmar nueva contraseña", { exact: true })
    .fill(replacement);
  await page.getByRole("button", { name: "Actualizar contraseña" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await expect(
    page.getByText("Contraseña actualizada. Inicia sesión de nuevo."),
  ).toBeVisible();
  expect((await readCurrentUser(page)).status).toBe(401);
  expect(await page.getByLabel("Contraseña").inputValue()).toBe("");

  await page
    .getByRole("textbox", { name: "Usuario o correo" })
    .fill(account.username);
  await page.getByLabel("Contraseña").fill(replacement);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expectHome(page);
  const current = await readCurrentUser(page);
  expect(current.status).toBe(200);
  expect(current.user?.passwordChangeRequired).toBe(false);
});

test("R08 persistent unavailable branch context is restricted and logout remains available", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "BRANCH",
  });
  const currentUserRequests = countRequests(page, "/api/v1/auth/me");
  await signIn(page, account);
  await expect(
    page.getByRole("heading", { level: 1, name: "Acceso restringido" }),
  ).toBeVisible();
  expect(currentUserRequests()).toBe(3);
  await expect(page.getByText(account.fullName, { exact: true })).toHaveCount(
    0,
  );
  await expect(
    page.getByRole("button", { name: "Cerrar sesión" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Cerrar sesión" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  expect((await readCurrentUser(page)).status).toBe(401);
});

test("R09 stale security version removes a previously authenticated account", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  await signIn(page, account);
  await expectHome(page);
  await invalidateFixtureSecurityVersion(database, account.id);
  await page.waitForTimeout(5_100);
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await expect(page.getByText(account.fullName, { exact: true })).toHaveCount(
    0,
  );
  expect((await readCurrentUser(page)).status).toBe(401);
});

test("R10 account changes clear prior identity before rendering the next account", async ({
  page,
  database,
}) => {
  const first = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
    firstName: "Primera cuenta sintética",
    lastName: "Integración",
  });
  const second = await createStaffFixture(database, {
    role: "RECEPTIONIST",
    scope: "BRANCH",
    branchAssignments: 2,
    firstName: "Segunda cuenta sintética",
    lastName: "Integración",
  });
  await signIn(page, first);
  await expectHome(page);
  await openAccountMenu(page, first);
  await expect(page.getByRole("menu").last()).toContainText(first.fullName);
  await page.keyboard.press("Escape");
  await signOutThroughUi(page, first);
  await expect(page.getByText(first.fullName, { exact: true })).toHaveCount(0);

  await signIn(page, second);
  await expectHome(page);
  await expect(page.getByText(first.fullName, { exact: true })).toHaveCount(0);
  await openAccountMenu(page, second);
  await expect(page.getByRole("menu").last()).toContainText(second.fullName);
  await expect(page.getByText(first.fullName, { exact: true })).toHaveCount(0);
});

test("R11 logout in one tab invalidates the other tab through real session events", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  await signIn(page, account);
  await expectHome(page);
  const secondPage = await page.context().newPage();
  try {
    await secondPage.goto("/");
    await expectHome(secondPage);
    await expect(
      secondPage.getByRole("button", {
        name: `Menú de cuenta de ${account.fullName}`,
      }),
    ).toBeVisible();

    await signOutThroughUi(page, account);
    await expect(
      secondPage.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
    ).toBeVisible();
    await expect(
      secondPage.getByText(account.fullName, { exact: true }),
    ).toHaveCount(0);
    expect((await readCurrentUser(secondPage)).status).toBe(401);
  } finally {
    await secondPage.close();
  }
});

test("R12 direct SPA routes load the built entry and preserve protected route behavior", async ({
  page,
  database,
}) => {
  await page.goto("/login");
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await page.goto("/");
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await page.goto("/cambio-contrasena-obligatorio");
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();

  const required = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
    passwordChangeRequired: true,
  });
  await page
    .getByRole("textbox", { name: "Usuario o correo" })
    .fill(required.username);
  await page.getByLabel("Contraseña").fill(required.password);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Actualiza tu contraseña" }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("heading", { level: 1, name: "Actualiza tu contraseña" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Cerrar sesión" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();

  const active = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  await signIn(page, active);
  await expectHome(page);
  await page.goto("/ruta-inexistente-f01");
  await expect(
    page.getByRole("heading", { level: 1, name: "Página no encontrada" }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("heading", { level: 1, name: "Página no encontrada" }),
  ).toBeVisible();
});
