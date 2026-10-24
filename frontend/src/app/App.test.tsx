import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";
import { AppProviders } from "./AppProviders";
import { createMemoryAppRouter } from "./router/AppRoutes";
import { server } from "../test/server";

describe("application entry point", () => {
  it("shows login when the server reports no current session", async () => {
    server.use(
      http.get("/api/v1/auth/me", () =>
        HttpResponse.json(
          { status: 401, code: "AUTHENTICATION_REQUIRED" },
          { status: 401 },
        ),
      ),
    );

    const router = createMemoryAppRouter(["/"]);
    render(
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>,
    );

    expect(
      await screen.findByRole("heading", { name: "Iniciar sesión" }),
    ).toBeVisible();
    expect(
      screen.getByRole("textbox", { name: "Usuario o correo" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Contraseña")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Activar tema oscuro" }),
    ).toBeInTheDocument();
  });
});
