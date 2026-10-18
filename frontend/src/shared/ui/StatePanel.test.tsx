import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Button } from "antd";
import { describe, expect, it, vi } from "vitest";
import { StatePanel } from "./StatePanel";

describe("StatePanel", () => {
  it("announces errors and keeps a retry action explicit", async () => {
    const onRetry = vi.fn();
    const user = userEvent.setup();
    render(
      <StatePanel
        action={<Button onClick={onRetry}>Intentar de nuevo</Button>}
        description="No se pudo completar la solicitud."
        title="No fue posible cargar"
        variant="error"
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(
      "No fue posible cargar",
    );
    await user.click(screen.getByRole("button", { name: "Intentar de nuevo" }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it("announces loading and does not give a non-error state alert semantics", () => {
    render(
      <StatePanel
        description="Los datos se están preparando."
        title="Cargando Inicio"
        variant="loading"
      />,
    );

    expect(screen.getByRole("status")).toHaveTextContent("Cargando");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
