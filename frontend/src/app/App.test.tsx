import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { App } from "./App";

describe("application entry point", () => {
  it("mounts the named main landmark without placeholder business content", () => {
    render(<App />);

    expect(
      screen.getByRole("heading", { level: 1, name: "Coach Gym" }),
    ).toBeInTheDocument();
  });
});
