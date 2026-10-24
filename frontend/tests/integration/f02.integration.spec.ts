import AxeBuilder from "@axe-core/playwright";
import type { Page } from "@playwright/test";
import {
  createInvitationFixture,
  createPasswordRecoveryFixture,
  createStaffFixture,
  expect,
  test,
  type StaffFixture,
  type StaffRole,
  type StaffScope,
} from "./fixtures";

const png = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/c6UAAAAASUVORK5CYII=",
  "base64",
);

function requiredRow<T>(rows: T[]): T {
  const row = rows[0];
  if (!row) throw new Error("The synthetic database result was empty.");
  return row;
}

async function signIn(
  page: Page,
  usernameOrEmail: string,
  password: string,
): Promise<void> {
  await page.goto("/login");
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await page
    .getByRole("textbox", { name: "Usuario o correo" })
    .fill(usernameOrEmail);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Inicio" }),
  ).toBeVisible();
}

async function signOut(page: Page, account: StaffFixture): Promise<void> {
  const trigger = page.getByRole("button", {
    name: `Menú de cuenta de ${account.fullName}`,
  });
  await trigger.focus();
  await page.keyboard.press("Enter");
  await page.getByRole("menuitem", { name: "Cerrar sesión" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
}

async function authStatus(page: Page): Promise<number> {
  return page.evaluate(async () => {
    const response = await fetch("/api/v1/auth/me", {
      credentials: "same-origin",
      cache: "no-store",
    });
    return response.status;
  });
}

async function expectNoAxeViolations(page: Page): Promise<void> {
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.violations.map(({ id }) => id)).toEqual([]);
}

async function expectNoHorizontalOverflow(page: Page): Promise<void> {
  const overflow = await page.evaluate(
    () =>
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(1);
}

test("P01 organization administrator profile remains self-scoped", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  await signIn(page, account.username, account.password);
  await page.goto("/mi-perfil");
  await expect(page.getByRole("heading", { name: "Mi perfil" })).toBeVisible();
  await expect(page.getByLabel("Nombre")).toHaveValue(
    "Operador de integración",
  );
  await expect(page.getByLabel("Apellido")).toHaveValue("Sintético");
  await expect(page.getByText(account.email, { exact: true })).toBeVisible();
  await expect(page.getByText("Administrador", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("textbox", { name: "Correo electrónico" }),
  ).toHaveCount(0);
  await expectNoAxeViolations(page);
});

test("P01 branch administrator profile remains self-scoped", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "BRANCH",
    branchAssignments: 1,
  });
  await signIn(page, account.username, account.password);
  await page.goto("/mi-perfil");
  await expect(page.getByRole("heading", { name: "Mi perfil" })).toBeVisible();
  await expect(page.getByLabel("Nombre")).toHaveValue(
    "Operador de integración",
  );
  await expect(page.getByText(account.email, { exact: true })).toBeVisible();
  await expect(page.getByText("Administrador", { exact: true })).toBeVisible();
  await expectNoAxeViolations(page);
});

test("P01 receptionist profile remains self-scoped", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "RECEPTIONIST",
    scope: "BRANCH",
    branchAssignments: 2,
  });
  await signIn(page, account.username, account.password);
  await page.goto("/mi-perfil");
  await expect(page.getByRole("heading", { name: "Mi perfil" })).toBeVisible();
  await expect(page.getByLabel("Nombre")).toHaveValue(
    "Operador de integración",
  );
  await expect(page.getByText(account.email, { exact: true })).toBeVisible();
  await expect(page.getByText("Recepción", { exact: true })).toBeVisible();
  await expectNoAxeViolations(page);
});

test("P26 responsive and local themes cover the profile and settings shell", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  await signIn(page, account.username, account.password);
  await page.goto("/mi-perfil");
  await expect(page.getByRole("heading", { name: "Mi perfil" })).toBeVisible();
  await page.setViewportSize({ width: 320, height: 800 });
  await expectNoHorizontalOverflow(page);
  await expectNoAxeViolations(page);
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto("/ajustes");
  await page.getByRole("button", { name: "Activar tema oscuro" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.getByRole("button", { name: "Activar tema claro" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
});

test("P03 mandatory password gate never loads profile or photo endpoints", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
    passwordChangeRequired: true,
  });
  const privateProfileRequests: string[] = [];
  page.on("request", (request) => {
    const pathname = new URL(request.url()).pathname;
    if (
      pathname === "/api/v1/me/profile" ||
      pathname.startsWith("/api/v1/me/profile/photo")
    ) {
      privateProfileRequests.push(pathname);
    }
  });

  await page.goto("/login");
  await page
    .getByRole("textbox", { name: "Usuario o correo" })
    .fill(account.username);
  await page.getByLabel("Contraseña").fill(account.password);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page.getByLabel("Contraseña actual")).toBeVisible();
  await page.goto("/mi-perfil");
  await expect(page.getByLabel("Contraseña actual")).toBeVisible();
  expect(privateProfileRequests).toEqual([]);
});

test("P04 P06 and P24 profile writes use CSRF, preserve drafts, and reconcile versions", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  await signIn(page, account.username, account.password);
  await page.goto("/mi-perfil");
  await expect(page.getByLabel("Nombre")).toHaveValue(
    "Operador de integración",
  );

  const missingCsrfStatus = await page.evaluate(async () => {
    const response = await fetch("/api/v1/me/profile", {
      method: "PUT",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        firstName: "No autorizado",
        lastName: "Sin token",
        version: 0,
      }),
    });
    return response.status;
  });
  expect(missingCsrfStatus).toBe(403);

  await database.query(
    `UPDATE gym.users
        SET first_name = 'Cambio del servidor',
            version = version + 1
      WHERE id = $1`,
    [account.id],
  );
  await page.getByLabel("Nombre").fill("Borrador conservado");
  await page.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(
    page.getByText("El perfil cambió en otra sesión. Tus cambios siguen aquí."),
  ).toBeVisible();
  await expect(page.getByLabel("Nombre")).toHaveValue("Borrador conservado");
  await page.getByRole("button", { name: "Recargar perfil" }).click();
  const reloadDialog = page.getByRole("dialog", { name: "Recargar el perfil" });
  await expect(reloadDialog).toBeVisible();
  await reloadDialog
    .getByRole("button", { name: "Descartar y recargar" })
    .click();
  await expect(page.getByLabel("Nombre")).toHaveValue("Cambio del servidor");

  await page.getByLabel("Nombre").fill("Nombre actualizado");
  await page.getByLabel("Apellido").fill("Identidad verificada");
  const saveProfile = page.getByRole("button", { name: "Guardar cambios" });
  await saveProfile.click();
  await expect(saveProfile).toHaveCount(0);
  await expect(page.getByLabel("Nombre")).toHaveValue("Nombre actualizado");
  const persisted = await database.query<{
    first_name: string;
    last_name: string;
    email: string;
    status: string;
    role_code: string;
    scope_type: string;
  }>(
    `SELECT u.first_name, u.last_name, u.email, u.status,
            role.role_code, scope.scope_type
       FROM gym.users u
       JOIN gym.user_roles ur ON ur.user_id = u.id
       JOIN gym.roles role ON role.id = ur.role_id
       JOIN gym.staff_scopes scope ON scope.user_id = u.id
      WHERE u.id = $1`,
    [account.id],
  );
  expect(persisted.rowCount).toBe(1);
  const persistedRow = requiredRow(persisted.rows);
  expect(persistedRow.first_name).toBe("Nombre actualizado");
  expect(persistedRow.last_name).toBe("Identidad verificada");
  expect(persistedRow.email).toBe(account.email);
  expect(persistedRow.status).toBe("ACTIVE");
  expect(persistedRow.role_code).toBe("ADMIN");
  expect(persistedRow.scope_type).toBe("ORGANIZATION");
});

test("P09 and P24 photo upload, private download, and removal use isolated storage and CSRF", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "RECEPTIONIST",
    scope: "BRANCH",
    branchAssignments: 1,
  });
  await signIn(page, account.username, account.password);
  await page.goto("/mi-perfil");
  await expect(page.getByRole("heading", { name: "Mi perfil" })).toBeVisible();

  await page.locator('input[type="file"]').setInputFiles({
    name: "synthetic-profile.png",
    mimeType: "image/png",
    buffer: png,
  });
  await expect(page.getByText(/Imagen seleccionada:/)).toBeVisible();
  await page.getByRole("button", { name: "Guardar foto" }).click();
  await expect(page.locator(".cg-profile-avatar img")).toHaveAttribute(
    "src",
    /^blob:/,
  );
  await expect(
    page.getByText("La foto de perfil se actualizó.", { exact: true }),
  ).toBeVisible();
  const photo = await database.query<{
    content_type: string;
    size_bytes: string;
    version: string;
  }>(
    `SELECT content_type, size_bytes, version
       FROM gym.staff_profile_photos
      WHERE user_id = $1`,
    [account.id],
  );
  expect(photo.rowCount).toBe(1);
  const photoRow = requiredRow(photo.rows);
  expect(photoRow.content_type).toBe("image/png");
  expect(Number(photoRow.size_bytes)).toBe(png.length);

  const missingCsrfStatus = await page.evaluate(async () => {
    const response = await fetch("/api/v1/me/profile/photo?version=1", {
      method: "DELETE",
      credentials: "same-origin",
    });
    return response.status;
  });
  expect(missingCsrfStatus).toBe(403);
  await page.getByRole("button", { name: "Eliminar foto" }).click();
  const removeDialog = page.getByRole("dialog", {
    name: "Eliminar foto de perfil",
  });
  await removeDialog.getByRole("button", { name: "Eliminar foto" }).click();
  await expect(page.locator(".cg-profile-avatar img")).toHaveCount(0);
  expect(
    (
      await database.query(
        "SELECT id FROM gym.staff_profile_photos WHERE user_id = $1",
        [account.id],
      )
    ).rowCount,
  ).toBe(0);
  const missingPhotoStatus = await page.evaluate(async () => {
    const response = await fetch("/api/v1/me/profile/photo", {
      credentials: "same-origin",
      cache: "no-store",
    });
    return response.status;
  });
  expect(missingPhotoStatus).toBe(404);
});

test("P13 voluntary password change invalidates the session and requires manual login", async ({
  page,
  database,
}) => {
  test.setTimeout(120_000);
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  const newPassword = "New-Synthetic-2026!";
  await signIn(page, account.username, account.password);
  await page.goto("/mi-cuenta/contrasena");
  await page.getByLabel("Contraseña actual").fill(account.password);
  await page.getByLabel("Nueva contraseña", { exact: true }).fill(newPassword);
  await page
    .getByLabel("Confirmar nueva contraseña", { exact: true })
    .fill(newPassword);
  const updatePassword = page.getByRole("button", {
    name: "Cambiar contraseña",
  });
  await expect(updatePassword).toBeEnabled();
  await updatePassword.click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  expect(await authStatus(page)).toBe(401);

  const security = await database.query<{ security_version: string }>(
    "SELECT security_version FROM gym.users WHERE id = $1",
    [account.id],
  );
  expect(requiredRow(security.rows).security_version).toBe("1");

  await signIn(page, account.username, newPassword);
  await signOut(page, { ...account, password: newPassword });
  await page.goto("/login");
  await page
    .getByRole("textbox", { name: "Usuario o correo" })
    .fill(account.username);
  await page.getByLabel("Contraseña").fill(account.password);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  expect(await authStatus(page)).toBe(401);
});

test("P16 and P17 recovery requests stay generic and a real synthetic reset is single-use", async ({
  page,
  database,
}) => {
  test.setTimeout(120_000);
  const knownAccount = await createStaffFixture(database, {
    role: "RECEPTIONIST",
    scope: "BRANCH",
    branchAssignments: 1,
  });
  const unknownEmail = `unknown-${knownAccount.id}@example.test`;
  const requestStatuses: number[] = [];
  page.on("response", (response) => {
    if (
      new URL(response.url()).pathname ===
      "/api/v1/auth/password-recovery-requests"
    ) {
      requestStatuses.push(response.status());
    }
  });

  await page.goto("/solicitar-recuperacion");
  await page.getByLabel("Correo electrónico").fill(knownAccount.email);
  await page.getByRole("button", { name: "Enviar instrucciones" }).click();
  const genericMessage = page.getByText(
    "Si existe una cuenta de personal activa con ese correo, recibirás instrucciones para recuperar tu contraseña.",
  );
  await expect(genericMessage).toBeVisible();
  const knownMessage = await genericMessage.innerText();
  await page.getByLabel("Correo electrónico").fill(unknownEmail);
  await page.getByRole("button", { name: "Enviar instrucciones" }).click();
  await expect(genericMessage).toBeVisible();
  expect(await genericMessage.innerText()).toBe(knownMessage);
  expect(requestStatuses).toEqual([202, 202]);
  await expectNoAxeViolations(page);

  const resetAccount = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  const recovery = await createPasswordRecoveryFixture(
    database,
    resetAccount.id,
  );
  const password = "Recovery-Synthetic-2026!";
  await page.goto("/solicitar-recuperacion");
  await page.goto(`/recover-password#token=${recovery.token}`);
  await expect(
    page.getByRole("heading", { name: "Define una contraseña nueva" }),
  ).toBeVisible();
  await expect
    .poll(() => page.url())
    .toBe("http://127.0.0.1:5183/recover-password");

  const missingCsrfStatus = await page.evaluate(
    async ({ token, passwordValue }) => {
      const response = await fetch("/api/v1/auth/password-recovery/complete", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          token,
          newPassword: passwordValue,
          passwordConfirmation: passwordValue,
        }),
      });
      return response.status;
    },
    { token: recovery.token, passwordValue: password },
  );
  expect(missingCsrfStatus).toBe(403);
  const pendingState = await database.query<{ status: string }>(
    "SELECT status FROM gym.staff_password_recovery_tokens WHERE id = $1",
    [recovery.id],
  );
  expect(requiredRow(pendingState.rows).status).toBe("PENDING");

  await page.getByLabel("Contraseña nueva", { exact: true }).fill(password);
  await page
    .getByLabel("Confirma la contraseña nueva", { exact: true })
    .fill(password);
  await page.getByRole("button", { name: "Actualizar contraseña" }).click();
  await expect(
    page.getByRole("heading", { name: "Contraseña actualizada" }),
  ).toBeVisible();
  expect(await authStatus(page)).toBe(401);
  const usedState = await database.query<{
    status: string;
    security_version: string;
  }>(
    `SELECT token.status, staff_user.security_version
       FROM gym.staff_password_recovery_tokens token
       JOIN gym.users staff_user ON staff_user.id = token.user_id
      WHERE token.id = $1`,
    [recovery.id],
  );
  expect(usedState.rows[0]).toMatchObject({
    status: "USED",
    security_version: "1",
  });

  await page.goto("/solicitar-recuperacion");
  await page.goto(`/recover-password#token=${recovery.token}`);
  await page.getByLabel("Contraseña nueva", { exact: true }).fill(password);
  await page
    .getByLabel("Confirma la contraseña nueva", { exact: true })
    .fill(password);
  await page.getByRole("button", { name: "Actualizar contraseña" }).click();
  await expect(
    page.getByText(
      "El enlace de recuperación no está disponible. Solicita uno nuevo.",
    ),
  ).toBeVisible();
  expect(await authStatus(page)).toBe(401);
  await signIn(page, resetAccount.username, password);
});

test("P21 through P24 invitation preview and acceptance enforce the stored proposal", async ({
  page,
  database,
}) => {
  test.setTimeout(120_000);
  const inviter = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  const branchIds = [
    process.env.CG_E2E_INITIAL_BRANCH_ID,
    process.env.CG_E2E_SECOND_BRANCH_ID,
  ].filter((value): value is string => Boolean(value));
  const initialBranchId = branchIds[0];
  const secondBranchId = branchIds[1];
  if (!initialBranchId || !secondBranchId) {
    throw new Error("The integration harness did not provide both branch IDs.");
  }
  const branchNames = await database.query<{ id: string; name: string }>(
    "SELECT id, name FROM gym.gym_branches WHERE id = ANY($1::uuid[])",
    [branchIds],
  );
  expect(branchNames.rowCount).toBe(2);
  const branchNameById = new Map(
    branchNames.rows.map(({ id, name }) => [id, name]),
  );
  const invitations = [
    await createInvitationFixture(database, {
      inviterUserId: inviter.id,
      role: "ADMIN",
      scope: "ORGANIZATION",
      branchIds: [],
    }),
    await createInvitationFixture(database, {
      inviterUserId: inviter.id,
      role: "ADMIN",
      scope: "BRANCH",
      branchIds: [initialBranchId],
    }),
    await createInvitationFixture(database, {
      inviterUserId: inviter.id,
      role: "RECEPTIONIST",
      scope: "BRANCH",
      branchIds,
    }),
  ];
  const proposals: Array<{
    role: StaffRole;
    scope: StaffScope;
    branchIds: string[];
  }> = [
    { role: "ADMIN", scope: "ORGANIZATION", branchIds: [] },
    { role: "ADMIN", scope: "BRANCH", branchIds: [initialBranchId] },
    { role: "RECEPTIONIST", scope: "BRANCH", branchIds },
  ];

  for (const [index, invitation] of invitations.entries()) {
    const proposal = proposals[index];
    if (!proposal)
      throw new Error("The invitation proposal fixture is missing.");
    await page.goto("/login");
    await page.goto(`/accept-invitation#token=${invitation.token}`);
    await expect(
      page.getByRole("heading", { name: "Aceptar invitación" }),
    ).toBeVisible();
    await expect
      .poll(() => page.url())
      .toBe("http://127.0.0.1:5183/accept-invitation");
    await page.getByRole("button", { name: "Revisar invitación" }).click();
    const summary = page.getByRole("region", {
      name: "Datos de la invitación",
    });
    await expect(summary).toBeVisible();
    const maskedEmail = await summary.getByText(/@example\.test$/).innerText();
    expect(maskedEmail).not.toBe(invitation.email);
    await expect(summary).toContainText(
      proposal.role === "ADMIN" ? "Administrador" : "Recepcionista",
    );
    await expect(summary).toContainText(
      proposal.scope === "ORGANIZATION"
        ? "Toda la organización"
        : "Sucursales específicas",
    );
    for (const branchId of proposal.branchIds) {
      await expect(summary).toContainText(branchNameById.get(branchId)!);
    }
    await expect(summary.locator("time")).toHaveAttribute("datetime", /Z$/);
    await expect(summary).not.toContainText(invitation.email);
    await expect(page.getByRole("textbox", { name: "Nombre" })).toBeVisible();
    await expectNoAxeViolations(page);
  }

  const revoked = await createInvitationFixture(database, {
    inviterUserId: inviter.id,
    role: "RECEPTIONIST",
    scope: "BRANCH",
    branchIds: [initialBranchId],
  });
  await database.query(
    `UPDATE gym.staff_invitations
        SET status = 'REVOKED', revoked_at = GREATEST(CURRENT_TIMESTAMP, last_sent_at),
            version = version + 1
      WHERE id = $1`,
    [revoked.id],
  );
  await page.goto("/login");
  await page.goto(`/accept-invitation#token=${revoked.token}`);
  await page.getByRole("button", { name: "Revisar invitación" }).click();
  await expect(
    page.getByText(
      "El enlace de invitación no está disponible. Solicita a administración que envíe uno nuevo.",
    ),
  ).toBeVisible();

  const acceptedInvitation = invitations[2];
  if (!acceptedInvitation) {
    throw new Error("The acceptance fixture was not created.");
  }
  const acceptancePassword = "Invitation-Synthetic-2026!";
  await page.goto("/login");
  await page.goto(`/accept-invitation#token=${acceptedInvitation.token}`);
  await page.getByRole("button", { name: "Revisar invitación" }).click();
  await expect(
    page.getByRole("region", { name: "Datos de la invitación" }),
  ).toBeVisible();

  const missingCsrfStatus = await page.evaluate(
    async ({ token, passwordValue }) => {
      const response = await fetch("/api/v1/staff-invitations/accept", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          token,
          firstName: "Integración",
          lastName: "Aceptada",
          password: passwordValue,
          passwordConfirmation: passwordValue,
        }),
      });
      return response.status;
    },
    { token: acceptedInvitation.token, passwordValue: acceptancePassword },
  );
  expect(missingCsrfStatus).toBe(403);

  await page.getByLabel("Nombre").fill("Integración");
  await page.getByLabel("Apellido").fill("Aceptada");
  await page.getByLabel("Contraseña", { exact: true }).fill(acceptancePassword);
  await page.getByLabel("Confirma la contraseña").fill(acceptancePassword);
  await page.getByRole("button", { name: "Aceptar invitación" }).click();
  await expect(
    page.getByRole("heading", { name: "Invitación aceptada" }),
  ).toBeVisible();
  expect(await authStatus(page)).toBe(401);

  const accepted = await database.query<{
    invitation_status: string;
    accepted_user_id: string;
    email: string;
    role_code: string;
    scope_type: string;
  }>(
    `SELECT invitation.status AS invitation_status,
            invitation.accepted_user_id, staff_user.email,
            role.role_code, scope.scope_type
       FROM gym.staff_invitations invitation
       JOIN gym.users staff_user ON staff_user.id = invitation.accepted_user_id
       JOIN gym.user_roles user_role ON user_role.user_id = staff_user.id
       JOIN gym.roles role ON role.id = user_role.role_id
       JOIN gym.staff_scopes scope ON scope.user_id = staff_user.id
      WHERE invitation.id = $1`,
    [acceptedInvitation.id],
  );
  expect(accepted.rowCount).toBe(1);
  const acceptedRow = requiredRow(accepted.rows);
  expect(acceptedRow).toMatchObject({
    invitation_status: "ACCEPTED",
    email: acceptedInvitation.email,
    role_code: "RECEPTIONIST",
    scope_type: "BRANCH",
  });
  const assignments = await database.query<{ branch_id: string }>(
    `SELECT branch_id
       FROM gym.staff_branch_assignments
      WHERE user_id = $1 AND status = 'ACTIVE'
      ORDER BY branch_id`,
    [acceptedRow.accepted_user_id],
  );
  expect(assignments.rows.map(({ branch_id }) => branch_id).sort()).toEqual(
    [...branchIds].sort(),
  );

  await signIn(page, acceptedInvitation.email, acceptancePassword);
  await expect(page.getByRole("heading", { name: "Inicio" })).toBeVisible();
});
