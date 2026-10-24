import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { RouterProvider } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { AppProviders } from "../../app/AppProviders";
import { createMemoryAppRouter } from "../../app/router/AppRoutes";
import type { CurrentUser } from "../../shared/api/contracts";
import { branchFixture, identityFixture } from "../../test/identity-fixtures";
import { server } from "../../test/server";

const profilePath = "/api/v1/me/profile";
const photoPath = "/api/v1/me/profile/photo";
const baseProfile = {
  userId: identityFixture.id,
  username: identityFixture.username,
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

function installSession(
  options: {
    user?: CurrentUser;
    passwordRequired?: boolean;
    anonymous?: boolean;
  } = {},
) {
  const currentUser: CurrentUser = {
    ...(identityFixture as CurrentUser),
    ...options.user,
    passwordChangeRequired: options.passwordRequired ?? false,
  };
  server.use(
    http.get("/api/v1/auth/me", () =>
      options.anonymous
        ? HttpResponse.json(
            { status: 401, code: "AUTHENTICATION_REQUIRED" },
            { status: 401 },
          )
        : HttpResponse.json(currentUser),
    ),
    http.get("/api/v1/me/branch-context", () =>
      HttpResponse.json({
        organizationId: branchFixture.organizationId,
        scopeType: currentUser.organizationScope,
        activeBranch: currentUser.activeBranch,
        availableBranches: currentUser.availableBranches,
      }),
    ),
    http.get("/api/v1/auth/csrf", () =>
      HttpResponse.json({
        token: "synthetic-csrf-profile",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      }),
    ),
  );
}

function renderApp(entries: string[]) {
  server.use(
    http.get("/api/v1/auth/csrf", () =>
      HttpResponse.json({
        token: "synthetic-csrf-profile",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      }),
    ),
  );
  const router = createMemoryAppRouter(entries);
  const view = render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...view, router };
}

function mockObjectUrls() {
  const createDescriptor = Object.getOwnPropertyDescriptor(
    URL,
    "createObjectURL",
  );
  const revokeDescriptor = Object.getOwnPropertyDescriptor(
    URL,
    "revokeObjectURL",
  );
  const created: Array<{ blob: Blob; url: string }> = [];
  const revoked: string[] = [];
  Object.defineProperty(URL, "createObjectURL", {
    configurable: true,
    value: (blob: Blob) => {
      const url = `blob:synthetic-${created.length + 1}`;
      created.push({ blob, url });
      return url;
    },
  });
  Object.defineProperty(URL, "revokeObjectURL", {
    configurable: true,
    value: (url: string) => revoked.push(url),
  });
  return {
    created,
    revoked,
    restore() {
      if (createDescriptor) {
        Object.defineProperty(URL, "createObjectURL", createDescriptor);
      } else {
        Reflect.deleteProperty(URL, "createObjectURL");
      }
      if (revokeDescriptor) {
        Object.defineProperty(URL, "revokeObjectURL", revokeDescriptor);
      } else {
        Reflect.deleteProperty(URL, "revokeObjectURL");
      }
    },
  };
}

function privatePhotoResponse() {
  return new HttpResponse(new Uint8Array([0x89, 0x50, 0x4e, 0x47]), {
    headers: {
      "Cache-Control": "private, no-store",
      "Content-Type": "image/png",
    },
  });
}

function interceptPhotoUploads(
  handler: (headers: Headers, body: FormData) => Response | Promise<Response>,
) {
  const nativeFetch = globalThis.fetch;
  return vi
    .spyOn(globalThis, "fetch")
    .mockImplementation(async (input, init) => {
      const url = new URL(input instanceof Request ? input.url : String(input));
      if (url.pathname === photoPath && init?.method === "PUT") {
        if (!(init.body instanceof FormData))
          throw new Error("Expected a multipart profile photo body.");
        return handler(new Headers(init.headers), init.body);
      }
      return nativeFetch(input, init);
    });
}

describe("F02 profile and photo flows", () => {
  it("loads own avatar metadata on Home but skips H03 when there is no photo", async () => {
    installSession();
    const profileReads = vi.fn();
    const photoReads = vi.fn();
    server.use(
      http.get(profilePath, () => {
        profileReads();
        return HttpResponse.json(baseProfile);
      }),
      http.get("/api/v1/me/profile/photo", () => {
        photoReads();
        return new HttpResponse(new Uint8Array([1]), {
          headers: { "Content-Type": "image/png" },
        });
      }),
    );
    renderApp(["/"]);
    expect(
      await screen.findByRole("heading", { name: "Inicio" }),
    ).toBeVisible();
    await waitFor(() => expect(profileReads).toHaveBeenCalledTimes(1));
    expect(photoReads).not.toHaveBeenCalled();
  });

  it("discards a pending private photo download when the session changes actor", async () => {
    const user = userEvent.setup();
    const urls = mockObjectUrls();
    const secondIdentity = {
      ...identityFixture,
      id: "00000000-0000-4000-8000-000000000009",
      username: "second-operator",
      fullName: "Otro operador",
    } as CurrentUser;
    let currentIdentity: CurrentUser = { ...identityFixture } as CurrentUser;
    let releasePhoto: ((response: Response) => void) | undefined;
    const photoReads = vi.fn();
    const originalProfile = {
      ...baseProfile,
      photoPresent: true,
      photoUrl: photoPath,
    };
    const secondProfile = {
      ...baseProfile,
      userId: secondIdentity.id,
      username: secondIdentity.username,
      firstName: "Otro",
      lastName: "Operador",
      displayName: secondIdentity.fullName,
    };

    server.use(
      http.get("/api/v1/auth/me", () => HttpResponse.json(currentIdentity)),
      http.get("/api/v1/me/branch-context", () =>
        HttpResponse.json({
          organizationId: branchFixture.organizationId,
          scopeType: currentIdentity.organizationScope,
          activeBranch: currentIdentity.activeBranch,
          availableBranches: currentIdentity.availableBranches,
        }),
      ),
      http.get(profilePath, () =>
        HttpResponse.json(
          currentIdentity.id === identityFixture.id
            ? originalProfile
            : secondProfile,
        ),
      ),
      http.get(photoPath, () => {
        photoReads();
        return new Promise<Response>((resolve) => {
          releasePhoto = resolve;
        });
      }),
    );

    const view = renderApp(["/mi-perfil"]);
    try {
      expect(await screen.findByLabelText("Nombre")).toHaveValue("Ana");
      await waitFor(() => expect(photoReads).toHaveBeenCalledTimes(1));

      await act(async () => {
        await view.router.navigate("/");
      });
      expect(
        await screen.findByRole("heading", { level: 1, name: "Inicio" }),
      ).toBeVisible();
      currentIdentity = secondIdentity;
      await user.click(
        screen.getByRole("button", { name: "Actualizar contexto" }),
      );

      expect(
        await screen.findByRole("button", {
          name: `Menú de cuenta de ${secondIdentity.fullName}`,
        }),
      ).toBeVisible();
      expect(document.querySelector(".cg-account-avatar img")).toBeNull();

      await act(async () => {
        releasePhoto?.(privatePhotoResponse());
        await Promise.resolve();
      });
      await waitFor(() =>
        expect(document.querySelector(".cg-account-avatar img")).toBeNull(),
      );
      expect(urls.created).toHaveLength(0);
    } finally {
      view.unmount();
      urls.restore();
    }
  }, 20_000);

  it.each([
    {
      label: "administrador de organización sin sucursal activa",
      user: { ...identityFixture, activeBranch: null } as CurrentUser,
      role: "Administrador",
    },
    {
      label: "administrador de sucursal",
      user: {
        ...identityFixture,
        organizationScope: "BRANCH" as const,
        activeBranch: branchFixture,
      } as CurrentUser,
      role: "Administrador",
    },
    {
      label: "recepcionista de sucursal",
      user: {
        ...identityFixture,
        roles: ["RECEPTIONIST"],
        organizationScope: "BRANCH" as const,
        activeBranch: branchFixture,
      } as CurrentUser,
      role: "Recepción",
    },
  ])("loads the own profile for $label", async ({ user, role }) => {
    installSession({ user });
    server.use(
      http.get(profilePath, () =>
        HttpResponse.json({ ...baseProfile, roles: user.roles }),
      ),
    );
    renderApp(["/mi-perfil"]);

    await screen.findByRole("heading", { name: "Mi perfil" });
    await screen.findByLabelText("Nombre");
    expect(screen.getByLabelText("Nombre")).toHaveValue("Ana");
    expect(screen.getByLabelText("Apellido")).toHaveValue("Ríos");
    expect(screen.getByText(role)).toBeVisible();
    expect(screen.getByText("operator@example.test")).toBeVisible();
    expect(screen.getByText("operador-prueba")).toBeVisible();
    expect(
      screen.getByRole("button", {
        name: "Menú de cuenta de Operador de prueba",
      }),
    ).toBeVisible();
  });

  it("normalizes and saves only first/last name and server version, then refreshes the header identity", async () => {
    const user = userEvent.setup();
    let profile = { ...baseProfile };
    let currentIdentity = { ...identityFixture };
    const update = vi.fn();
    const photoReads = vi.fn();
    server.use(
      http.get("/api/v1/auth/me", () => HttpResponse.json(currentIdentity)),
      http.get("/api/v1/me/branch-context", () =>
        HttpResponse.json({
          organizationId: branchFixture.organizationId,
          scopeType: currentIdentity.organizationScope,
          activeBranch: currentIdentity.activeBranch,
          availableBranches: currentIdentity.availableBranches,
        }),
      ),
      http.get(profilePath, () => HttpResponse.json(profile)),
      http.put(profilePath, async ({ request }) => {
        const body = await request.json();
        update(body);
        expect(body).toEqual({
          firstName: "Ana María",
          lastName: "Ríos",
          version: 4,
        });
        profile = {
          ...profile,
          firstName: "Ana María",
          displayName: "Ana María Ríos",
          version: 5,
        };
        currentIdentity = { ...currentIdentity, fullName: profile.displayName };
        return HttpResponse.json(profile);
      }),
      http.get("/api/v1/me/profile/photo", () => {
        photoReads();
        return HttpResponse.json({});
      }),
    );

    renderApp(["/mi-perfil"]);
    await screen.findByRole("heading", { name: "Mi perfil" });
    await screen.findByLabelText("Nombre");
    await user.clear(screen.getByLabelText("Nombre"));
    await user.type(screen.getByLabelText("Nombre"), " Ana   María ");
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));

    await waitFor(() => expect(update).toHaveBeenCalledTimes(1));
    expect(
      await screen.findByRole("button", {
        name: "Menú de cuenta de Ana María Ríos",
      }),
    ).toBeVisible();
    expect(await screen.findByLabelText("Nombre")).toHaveValue("Ana María");
    expect(photoReads).not.toHaveBeenCalled();
  }, 20_000);

  it("does not request profile data for anonymous or password-required sessions", async () => {
    const profileReads = vi.fn();
    installSession({ anonymous: true });
    server.use(
      http.get(profilePath, () => {
        profileReads();
        return HttpResponse.json(baseProfile);
      }),
    );
    const first = renderApp(["/mi-perfil"]);
    expect(
      await screen.findByRole("heading", { name: "Iniciar sesión" }),
    ).toBeVisible();
    expect(profileReads).not.toHaveBeenCalled();
    first.unmount();

    server.use(
      http.get("/api/v1/auth/me", () =>
        HttpResponse.json({ ...identityFixture, passwordChangeRequired: true }),
      ),
      http.get("/api/v1/me/branch-context", () =>
        HttpResponse.json({
          organizationId: branchFixture.organizationId,
          scopeType: "ORGANIZATION",
          activeBranch: null,
          availableBranches: [branchFixture],
        }),
      ),
    );
    const second = renderApp(["/mi-perfil"]);
    expect(
      await screen.findByRole("heading", { name: "Actualiza tu contraseña" }),
    ).toBeVisible();
    expect(profileReads).not.toHaveBeenCalled();
    second.unmount();
  });

  it("preserves a dirty draft on version conflict and requires explicit discard before reload", async () => {
    const user = userEvent.setup();
    let reads = 0;
    const update = vi.fn();
    server.use(
      http.get("/api/v1/auth/me", () => HttpResponse.json(identityFixture)),
      http.get(profilePath, () => {
        reads += 1;
        return HttpResponse.json(
          reads === 1
            ? baseProfile
            : {
                ...baseProfile,
                firstName: "Laura",
                displayName: "Laura Ríos",
                version: 5,
              },
        );
      }),
      http.put(profilePath, () => {
        update();
        return HttpResponse.json(
          { status: 409, code: "STAFF_PROFILE_VERSION_CONFLICT" },
          { status: 409 },
        );
      }),
    );
    renderApp(["/mi-perfil"]);
    await screen.findByRole("heading", { name: "Mi perfil" });
    await screen.findByLabelText("Nombre");
    await user.clear(screen.getByLabelText("Nombre"));
    await user.type(screen.getByLabelText("Nombre"), "Ana María");
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(
      await screen.findByText(
        "El perfil cambió en otra sesión. Tus cambios siguen aquí.",
      ),
    ).toBeVisible();
    expect(screen.getByLabelText("Nombre")).toHaveValue("Ana María");
    expect(update).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole("button", { name: "Recargar perfil" }));
    expect(
      await screen.findByRole("dialog", { name: "Recargar el perfil" }),
    ).toBeInTheDocument();
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Descartar y recargar",
      }),
    );
    await waitFor(() =>
      expect(screen.getByLabelText("Nombre")).toHaveValue("Laura"),
    );
    expect(update).toHaveBeenCalledTimes(1);
  });

  it("blocks browser history navigation until the user keeps editing or discards changes", async () => {
    const user = userEvent.setup();
    installSession();
    server.use(http.get(profilePath, () => HttpResponse.json(baseProfile)));
    const { router } = renderApp(["/", "/mi-perfil"]);
    await screen.findByRole("heading", { name: "Mi perfil" });
    await screen.findByLabelText("Nombre");
    await user.clear(screen.getByLabelText("Nombre"));
    await user.type(screen.getByLabelText("Nombre"), "Ana María");

    await act(async () => {
      await router.navigate(-1);
    });
    const dialog = await screen.findByRole("dialog", {
      name: "Tienes cambios sin guardar",
    });
    await user.click(
      within(dialog).getByRole("button", { name: "Seguir editando" }),
    );
    expect(screen.getByLabelText("Nombre")).toHaveValue("Ana María");

    await act(async () => {
      await router.navigate(-1);
    });
    const secondDialog = await screen.findByRole("dialog", {
      name: "Tienes cambios sin guardar",
    });
    await user.click(
      within(secondDialog).getByRole("button", { name: "Descartar y salir" }),
    );
    expect(
      await screen.findByRole("heading", { name: "Inicio" }),
    ).toBeVisible();
  });

  it("stages and cancels a preview, then uploads with CSRF/version and syncs both avatars", async () => {
    const user = userEvent.setup();
    const urls = mockObjectUrls();
    installSession();
    let profile = {
      ...baseProfile,
      photoPresent: true,
      photoUrl: photoPath as string | null,
      version: 7,
    };
    let profileReads = 0;
    let photoReads = 0;
    let uploads = 0;
    const uploadFetch = interceptPhotoUploads((headers, form) => {
      uploads += 1;
      expect(headers.get("X-XSRF-TOKEN")).toBe("synthetic-csrf-profile");
      expect(form.get("version")).toBe("7");
      expect(form.get("file")).toBeInstanceOf(File);
      expect((form.get("file") as File).name).toBe("profile-photo");
      profile = { ...profile, version: 8 };
      return new Response(
        JSON.stringify({
          photoId: "00000000-0000-4000-8000-000000000004",
          contentType: "image/png",
          sizeBytes: 10,
          updatedAt: "2026-10-22T12:00:00Z",
          version: 8,
        }),
        { headers: { "Content-Type": "application/json" } },
      );
    });
    server.use(
      http.get(profilePath, () => {
        profileReads += 1;
        return HttpResponse.json(profile);
      }),
      http.get(photoPath, () => {
        photoReads += 1;
        return privatePhotoResponse();
      }),
    );

    const view = renderApp(["/mi-perfil"]);
    try {
      await screen.findByRole("heading", { name: "Mi perfil" });
      await screen.findByLabelText("Nombre");
      await waitFor(() => {
        expect(
          document.querySelector(".cg-account-avatar img"),
        ).toHaveAttribute("src", "blob:synthetic-1");
        expect(
          document.querySelector(".cg-profile-avatar img"),
        ).toHaveAttribute("src", "blob:synthetic-1");
      });

      const firstFile = new File(["synthetic image"], "new-photo.png", {
        type: "image/png",
      });
      const fileInput =
        document.querySelector<HTMLInputElement>('input[type="file"]');
      expect(fileInput).not.toBeNull();
      await user.upload(fileInput!, firstFile);
      await screen.findByText(/Imagen seleccionada: new-photo.png/);
      expect(uploads).toBe(0);
      expect(document.querySelector(".cg-profile-avatar img")).toHaveAttribute(
        "src",
        "blob:synthetic-2",
      );

      await user.click(
        screen.getByRole("button", { name: "Cancelar selección" }),
      );
      await waitFor(() => expect(urls.revoked).toContain("blob:synthetic-2"));
      expect(document.querySelector(".cg-profile-avatar img")).toHaveAttribute(
        "src",
        "blob:synthetic-1",
      );

      const secondFile = new File(["replacement image"], "replacement.png", {
        type: "image/png",
      });
      const replacementInput =
        document.querySelector<HTMLInputElement>('input[type="file"]');
      expect(replacementInput).not.toBeNull();
      await user.upload(replacementInput!, secondFile);
      await user.click(screen.getByRole("button", { name: "Guardar foto" }));

      await waitFor(() => expect(uploads).toBe(1));
      await waitFor(() => expect(profileReads).toBe(2));
      await waitFor(() => expect(photoReads).toBe(2));
      expect(
        await screen.findByText("La foto de perfil se actualizó."),
      ).toBeVisible();
      expect(screen.queryByText(/Imagen seleccionada:/)).toBeNull();
      await waitFor(() => {
        const headerImage = document.querySelector(".cg-account-avatar img");
        const profileImage = document.querySelector(".cg-profile-avatar img");
        expect(headerImage).toBeInTheDocument();
        expect(profileImage).toBeInTheDocument();
        const sharedUrl = headerImage?.getAttribute("src");
        expect(sharedUrl).toMatch(/^blob:synthetic-\d+$/);
        expect(profileImage).toHaveAttribute("src", sharedUrl);
      });
      expect(urls.revoked).toContain("blob:synthetic-1");
    } finally {
      view.unmount();
      uploadFetch.mockRestore();
      urls.restore();
    }
  }, 20_000);

  it("removes the photo with the current version and returns both avatars to initials", async () => {
    const user = userEvent.setup();
    const urls = mockObjectUrls();
    installSession();
    let profile = {
      ...baseProfile,
      photoPresent: true,
      photoUrl: photoPath as string | null,
      version: 6,
    };
    const removals = vi.fn();
    server.use(
      http.get(profilePath, () => HttpResponse.json(profile)),
      http.get(photoPath, privatePhotoResponse),
      http.delete(photoPath, async ({ request }) => {
        removals(new URL(request.url).searchParams.get("version"));
        expect(request.headers.get("X-XSRF-TOKEN")).toBe(
          "synthetic-csrf-profile",
        );
        expect(await request.text()).toBe("");
        profile = {
          ...profile,
          photoPresent: false,
          photoUrl: null,
          version: 7,
        };
        return HttpResponse.json(profile);
      }),
    );

    const view = renderApp(["/mi-perfil"]);
    try {
      await screen.findByRole("heading", { name: "Mi perfil" });
      await screen.findByLabelText("Nombre");
      await waitFor(() =>
        expect(
          document.querySelector(".cg-account-avatar img"),
        ).toBeInTheDocument(),
      );
      await user.click(screen.getByRole("button", { name: "Eliminar foto" }));
      const dialog = await screen.findByRole("dialog", {
        name: "Eliminar foto de perfil",
      });
      await user.click(
        within(dialog).getByRole("button", { name: "Eliminar foto" }),
      );

      await waitFor(() => expect(removals).toHaveBeenCalledWith("6"));
      await waitFor(() => {
        expect(document.querySelector(".cg-account-avatar img")).toBeNull();
        expect(document.querySelector(".cg-profile-avatar img")).toBeNull();
      });
      expect(document.querySelector(".cg-account-avatar")).toHaveAttribute(
        "data-initials",
        "OP",
      );
      expect(document.querySelector(".cg-profile-avatar")).toHaveAttribute(
        "data-initials",
        "AR",
      );
      expect(urls.revoked).toContain("blob:synthetic-1");
      expect(screen.getByText("La foto de perfil se eliminó.")).toBeVisible();
    } finally {
      view.unmount();
      urls.restore();
    }
  }, 20_000);

  it("reconciles an uncertain upload before allowing an explicit retry", async () => {
    const user = userEvent.setup();
    installSession();
    let profile = {
      ...baseProfile,
      photoUrl: null as string | null,
      version: 4,
    };
    const profileReads = vi.fn();
    let uploads = 0;
    const submittedVersions: string[] = [];
    const uploadFetch = interceptPhotoUploads((_, form) => {
      uploads += 1;
      submittedVersions.push(String(form.get("version")));
      if (uploads === 1) {
        profile = { ...profile, version: 5 };
        return new Response(
          JSON.stringify({
            status: 500,
            code: "STAFF_PROFILE_STORAGE_FAILED",
          }),
          {
            status: 500,
            headers: { "Content-Type": "application/problem+json" },
          },
        );
      }
      profile = {
        ...profile,
        photoPresent: true,
        photoUrl: photoPath,
        version: 6,
      };
      return new Response(
        JSON.stringify({
          photoId: "00000000-0000-4000-8000-000000000004",
          contentType: "image/png",
          sizeBytes: 10,
          updatedAt: "2026-10-22T12:00:00Z",
          version: 6,
        }),
        { headers: { "Content-Type": "application/json" } },
      );
    });
    server.use(
      http.get(profilePath, () => {
        profileReads();
        return HttpResponse.json(profile);
      }),
      http.get(photoPath, privatePhotoResponse),
    );

    const view = renderApp(["/mi-perfil"]);
    try {
      await screen.findByRole("heading", { name: "Mi perfil" });
      await screen.findByLabelText("Nombre");
      const fileInput =
        document.querySelector<HTMLInputElement>('input[type="file"]');
      expect(fileInput).not.toBeNull();
      await user.upload(
        fileInput!,
        new File(["synthetic image"], "retry.png", { type: "image/png" }),
      );
      await user.click(screen.getByRole("button", { name: "Guardar foto" }));

      expect(
        await screen.findByText("No se pudo confirmar el cambio de foto."),
      ).toBeVisible();
      await waitFor(() => expect(profileReads).toHaveBeenCalledTimes(2));
      expect(uploads).toBe(1);
      expect(submittedVersions).toEqual(["4"]);
      await waitFor(() =>
        expect(
          screen.getByRole("button", { name: /Guardar foto/ }),
        ).toBeEnabled(),
      );

      await user.click(screen.getByRole("button", { name: /Guardar foto/ }));
      await waitFor(() => expect(uploads).toBe(2));
      expect(submittedVersions).toEqual(["4", "5"]);
    } finally {
      view.unmount();
      uploadFetch.mockRestore();
    }
  }, 20_000);

  it("shows fallback for a missing private photo without retrying H03", async () => {
    let photoReads = 0;
    installSession();
    server.use(
      http.get(profilePath, () =>
        HttpResponse.json({
          ...baseProfile,
          photoPresent: true,
          photoUrl: photoPath,
        }),
      ),
      http.get(photoPath, () => {
        photoReads += 1;
        return HttpResponse.json(
          { status: 404, code: "STAFF_PROFILE_PHOTO_NOT_FOUND" },
          { status: 404 },
        );
      }),
    );
    renderApp(["/mi-perfil"]);
    expect(
      await screen.findByText("No se pudo mostrar la foto actual."),
    ).toBeVisible();
    expect(document.querySelector(".cg-account-avatar img")).toBeNull();
    expect(document.querySelector(".cg-account-avatar")).toHaveAttribute(
      "data-initials",
      "OP",
    );
    await new Promise((resolve) => setTimeout(resolve, 40));
    expect(photoReads).toBe(1);
  });
});
