import { createContext, useContext } from "react";
import type { ThemeMode, ThemePreference } from "./tokens";

export interface ThemeContextValue {
  mode: ThemeMode;
  preference: ThemePreference;
  toggleTheme(): void;
}

export const ThemeContext = createContext<ThemeContextValue | null>(null);

export function useTheme(): ThemeContextValue {
  const value = useContext(ThemeContext);
  if (!value) {
    throw new Error("useTheme debe usarse dentro de ThemeProvider.");
  }
  return value;
}
