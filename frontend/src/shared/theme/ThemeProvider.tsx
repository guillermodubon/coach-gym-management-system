import { useEffect, useMemo, useState } from "react";
import type { PropsWithChildren } from "react";
import { App as AntApp, ConfigProvider } from "antd";
import esES from "antd/locale/es_ES";
import {
  applyThemeToDocument,
  parseThemePreference,
  readSystemTheme,
  readThemePreference,
  resolveTheme,
  THEME_STORAGE_KEY,
  writeThemePreference,
} from "./theme-preference";
import { getThemeConfig, type ThemeMode } from "./tokens";
import { ThemeContext } from "./useTheme";

function useSystemMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => {
    try {
      return window.matchMedia(query).matches;
    } catch {
      return false;
    }
  });

  useEffect(() => {
    let media: MediaQueryList;
    try {
      media = window.matchMedia(query);
    } catch {
      return;
    }

    const update = (event: MediaQueryListEvent) => setMatches(event.matches);
    if (typeof media.addEventListener === "function") {
      media.addEventListener("change", update);
    } else {
      media.addListener(update);
    }

    return () => {
      if (typeof media.removeEventListener === "function") {
        media.removeEventListener("change", update);
      } else {
        media.removeListener(update);
      }
    };
  }, [query]);

  return matches;
}

export function ThemeProvider({ children }: PropsWithChildren) {
  const [preference, setPreference] = useState(readThemePreference);
  const [systemTheme, setSystemTheme] = useState(readSystemTheme);
  const reducedMotion = useSystemMediaQuery("(prefers-reduced-motion: reduce)");
  const mode: ThemeMode = resolveTheme(preference, systemTheme);

  useEffect(() => {
    try {
      const media = window.matchMedia("(prefers-color-scheme: dark)");
      const update = (event: MediaQueryListEvent) => {
        setSystemTheme(event.matches ? "dark" : "light");
      };
      if (typeof media.addEventListener === "function") {
        media.addEventListener("change", update);
      } else {
        media.addListener(update);
      }
      return () => {
        if (typeof media.removeEventListener === "function") {
          media.removeEventListener("change", update);
        } else {
          media.removeListener(update);
        }
      };
    } catch {
      return;
    }
  }, []);

  useEffect(() => {
    function handleStorage(event: StorageEvent) {
      if (event.key === THEME_STORAGE_KEY || event.key === null) {
        setPreference(
          parseThemePreference(event.key === null ? null : event.newValue),
        );
      }
    }

    window.addEventListener("storage", handleStorage);
    return () => window.removeEventListener("storage", handleStorage);
  }, []);

  useEffect(() => {
    applyThemeToDocument(mode);
  }, [mode]);

  const context = useMemo(
    () => ({
      mode,
      preference,
      toggleTheme() {
        const next = mode === "light" ? "dark" : "light";
        setPreference(next);
        writeThemePreference(next);
      },
    }),
    [mode, preference],
  );
  const themeConfig = useMemo(
    () => getThemeConfig(mode, reducedMotion),
    [mode, reducedMotion],
  );

  return (
    <ThemeContext.Provider value={context}>
      <ConfigProvider locale={esES} theme={themeConfig}>
        <AntApp>{children}</AntApp>
      </ConfigProvider>
    </ThemeContext.Provider>
  );
}
