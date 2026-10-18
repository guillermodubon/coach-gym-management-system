import { themePalette, type ThemeMode, type ThemePreference } from "./tokens";

export const THEME_STORAGE_KEY = "coach-gym.theme";

export function parseThemePreference(value: string | null): ThemePreference {
  return value === "light" || value === "dark" ? value : null;
}

export function readThemePreference(
  storage?: Pick<Storage, "getItem">,
): ThemePreference {
  try {
    const target = storage ?? window.localStorage;
    return parseThemePreference(target.getItem(THEME_STORAGE_KEY));
  } catch {
    return null;
  }
}

export function writeThemePreference(
  preference: Exclude<ThemePreference, null>,
  storage?: Pick<Storage, "setItem">,
): void {
  try {
    const target = storage ?? window.localStorage;
    target.setItem(THEME_STORAGE_KEY, preference);
  } catch {
    // Keep the selection in React state when browser storage is unavailable.
  }
}

export function readSystemTheme(): ThemeMode {
  try {
    return window.matchMedia("(prefers-color-scheme: dark)").matches
      ? "dark"
      : "light";
  } catch {
    return "light";
  }
}

export function resolveTheme(
  preference: ThemePreference,
  systemTheme: ThemeMode,
): ThemeMode {
  return preference ?? systemTheme;
}

export function applyThemeToDocument(
  mode: ThemeMode,
  target: Document | undefined = typeof document === "undefined"
    ? undefined
    : document,
): void {
  if (!target) return;

  const colors = themePalette[mode];
  const root = target.documentElement;
  root.dataset.theme = mode;
  root.style.colorScheme = mode;
  root.style.setProperty("--cg-color-primary", colors.primary);
  root.style.setProperty("--cg-color-text", colors.text);
  root.style.setProperty("--cg-layout-background", colors.layout);
  root.style.setProperty("--cg-container-background", colors.container);
  root.style.setProperty("--cg-border-color", colors.border);
  root.style.setProperty("--cg-focus-color", colors.primary);
}

export function applyInitialTheme(): void {
  const preference = readThemePreference();
  applyThemeToDocument(resolveTheme(preference, readSystemTheme()));
}
