import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setMockMediaQuery } from "../../test/browser-mocks";
import { ThemeProvider } from "./ThemeProvider";
import { ThemeToggle } from "./ThemeToggle";
import { THEME_STORAGE_KEY } from "./theme-preference";
import { getThemeConfig } from "./tokens";
import { useTheme } from "./useTheme";

function ThemeDetails() {
  const { mode, preference } = useTheme();
  return <output>{`${mode}:${preference ?? "system"}`}</output>;
}

function Subject() {
  return (
    <ThemeProvider>
      <ThemeToggle />
      <ThemeDetails />
    </ThemeProvider>
  );
}

describe("ThemeProvider", () => {
  beforeEach(() => {
    setMockMediaQuery("(prefers-color-scheme: dark)", false);
    setMockMediaQuery("(prefers-reduced-motion: reduce)", false);
  });

  afterEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute("data-theme");
    document.documentElement.style.cssText = "";
    vi.restoreAllMocks();
  });

  it("follows the system when there is no stored preference", () => {
    setMockMediaQuery("(prefers-color-scheme: dark)", true);

    render(<Subject />);

    expect(screen.getByText("dark:system")).toBeInTheDocument();
    expect(document.documentElement).toHaveAttribute("data-theme", "dark");
  });

  it("honors an explicit preference and toggles with an accessible label", async () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, "light");
    const user = userEvent.setup();
    render(<Subject />);

    await user.click(
      screen.getByRole("button", { name: "Activar tema oscuro" }),
    );

    expect(screen.getByText("dark:dark")).toBeInTheDocument();
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark");
    expect(document.documentElement).toHaveStyle({ colorScheme: "dark" });
    expect(
      screen.getByRole("button", { name: "Activar tema claro" }),
    ).toBeInTheDocument();
  });

  it("treats invalid stored values as a system preference", () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, "sepia");

    render(<Subject />);

    expect(screen.getByText("light:system")).toBeInTheDocument();
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("sepia");
  });

  it("tracks system changes only while no explicit choice is stored", () => {
    render(<Subject />);

    act(() => setMockMediaQuery("(prefers-color-scheme: dark)", true));
    expect(screen.getByText("dark:system")).toBeInTheDocument();

    window.localStorage.setItem(THEME_STORAGE_KEY, "light");
    act(() => {
      window.dispatchEvent(
        new StorageEvent("storage", {
          key: THEME_STORAGE_KEY,
          newValue: "light",
        }),
      );
      setMockMediaQuery("(prefers-color-scheme: dark)", false);
      setMockMediaQuery("(prefers-color-scheme: dark)", true);
    });

    expect(screen.getByText("light:light")).toBeInTheDocument();
  });

  it("returns to the system theme when another tab removes the preference", () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, "dark");
    render(<Subject />);

    act(() =>
      window.dispatchEvent(
        new StorageEvent("storage", {
          key: THEME_STORAGE_KEY,
          newValue: null,
        }),
      ),
    );

    expect(screen.getByText("light:system")).toBeInTheDocument();
  });

  it("turns off component motion when reduced motion is requested", () => {
    expect(getThemeConfig("dark", true).token?.motion).toBe(false);
    expect(getThemeConfig("dark", false).token?.motion).toBeUndefined();
  });

  it("continues to render when local storage is unavailable", async () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("Blocked", "SecurityError");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("Blocked", "SecurityError");
    });
    const user = userEvent.setup();
    render(<Subject />);

    await user.click(
      screen.getByRole("button", { name: "Activar tema oscuro" }),
    );

    expect(screen.getByText("dark:dark")).toBeInTheDocument();
  });
});
