import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { RouterProvider } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { AppProviders } from "../../app/AppProviders";
import { createMemoryAppRouter } from "../../app/router/AppRoutes";
import { branchFixture, identityFixture } from "../../test/identity-fixtures";
import { server } from "../../test/server";
import { useBranchContext } from "./useBranchContext";

const profilePath = "/api/v1/me/profile";
const profile = {
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

function ContextProbe() {
  const context = useBranchContext();
  const transition = context.transition;
  return (
    <div>
      <button onClick={() => void context.selectBranch(branchFixture.id)}>
        Try context change
      </button>
      <output aria-label="Context transition status">
        {transition.status}
        {transition.status === "blocked" ? `:${transition.reason}` : ""}
      </output>
    </div>
  );
}

function renderProfileWithContextProbe() {
  const router = createMemoryAppRouter(["/mi-perfil"]);
  return render(
    <AppProviders>
      <ContextProbe />
      <RouterProvider router={router} />
    </AppProviders>,
  );
}

function installProfileSession() {
  server.use(
    http.get("/api/v1/auth/me", () => HttpResponse.json(identityFixture)),
    http.get(profilePath, () => HttpResponse.json(profile)),
    http.get("/api/v1/auth/csrf", () =>
      HttpResponse.json({
        token: "synthetic-context-guard-csrf",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      }),
    ),
  );
}

describe("F03 local context guards with F02 profile owners", () => {
  it("blocks a context mutation while the account profile has a dirty draft", async () => {
    const user = userEvent.setup();
    const branchContextWrites = vi.fn();
    installProfileSession();
    server.use(
      http.put("/api/v1/me/branch-context", () => {
        branchContextWrites();
        return HttpResponse.json({});
      }),
    );
    renderProfileWithContextProbe();

    const firstName = await screen.findByLabelText("Nombre");
    await user.clear(firstName);
    await user.type(firstName, "Ana María");
    await user.click(
      screen.getByRole("button", { name: "Try context change" }),
    );

    expect(
      await screen.findByLabelText("Context transition status"),
    ).toHaveTextContent("blocked:unsaved-changes");
    expect(branchContextWrites).not.toHaveBeenCalled();
  }, 20_000);

  it("blocks a context mutation while a private profile photo is staged", async () => {
    const user = userEvent.setup();
    const branchContextWrites = vi.fn();
    installProfileSession();
    server.use(
      http.put("/api/v1/me/branch-context", () => {
        branchContextWrites();
        return HttpResponse.json({});
      }),
    );
    renderProfileWithContextProbe();

    await screen.findByLabelText("Nombre");
    const fileInput = document.querySelector('input[type="file"]');
    expect(fileInput).toBeInstanceOf(HTMLInputElement);
    await user.upload(
      fileInput as HTMLInputElement,
      new File(["synthetic image bytes"], "profile.png", { type: "image/png" }),
    );
    await screen.findByText(/Imagen seleccionada: profile.png/);
    await user.click(
      screen.getByRole("button", { name: "Try context change" }),
    );

    expect(
      await screen.findByLabelText("Context transition status"),
    ).toHaveTextContent("blocked:unsaved-changes");
    expect(branchContextWrites).not.toHaveBeenCalled();
  }, 20_000);

  it("does not start a context mutation while a profile write holds the shared lease", async () => {
    const user = userEvent.setup();
    let finishProfileWrite: (() => void) | undefined;
    let profileWriteStarted: (() => void) | undefined;
    const started = new Promise<void>((resolve) => {
      profileWriteStarted = resolve;
    });
    const branchContextWrites = vi.fn();
    let currentIdentity = { ...identityFixture };
    let currentProfile = { ...profile };
    installProfileSession();
    server.use(
      http.get("/api/v1/auth/me", () => HttpResponse.json(currentIdentity)),
      http.get(profilePath, () => HttpResponse.json(currentProfile)),
      http.put(profilePath, async ({ request }) => {
        const body = (await request.json()) as {
          firstName: string;
          lastName: string;
          version: number;
        };
        profileWriteStarted?.();
        await new Promise<void>((resolve) => {
          finishProfileWrite = resolve;
        });
        currentProfile = {
          ...currentProfile,
          firstName: body.firstName,
          displayName: `${body.firstName} ${body.lastName}`,
          version: body.version + 1,
        };
        currentIdentity = {
          ...currentIdentity,
          fullName: currentProfile.displayName,
        };
        return HttpResponse.json(currentProfile);
      }),
      http.put("/api/v1/me/branch-context", () => {
        branchContextWrites();
        return HttpResponse.json({});
      }),
    );
    renderProfileWithContextProbe();

    const firstName = await screen.findByLabelText("Nombre");
    await user.clear(firstName);
    await user.type(firstName, "Ana María");
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
    await started;
    await user.click(
      screen.getByRole("button", { name: "Try context change" }),
    );

    expect(
      await screen.findByLabelText("Context transition status"),
    ).toHaveTextContent("blocked:write-in-progress");
    expect(branchContextWrites).not.toHaveBeenCalled();

    finishProfileWrite?.();
    await waitFor(() =>
      expect(screen.getByLabelText("Nombre")).toHaveValue("Ana María"),
    );
  }, 20_000);
});
