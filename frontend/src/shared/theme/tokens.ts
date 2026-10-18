import { theme, type ThemeConfig } from "antd";

export type ThemeMode = "light" | "dark";
export type ThemePreference = ThemeMode | null;

export const themePalette: Record<
  ThemeMode,
  {
    primary: string;
    primaryHover: string;
    primaryActive: string;
    text: string;
    textSecondary: string;
    layout: string;
    container: string;
    border: string;
    selected: string;
    selectedText: string;
    hover: string;
  }
> = {
  light: {
    primary: "#111111",
    primaryHover: "#2B2B2B",
    primaryActive: "#000000",
    text: "#141414",
    textSecondary: "#52525B",
    layout: "#F4F4F5",
    container: "#FFFFFF",
    border: "#D4D4D8",
    selected: "#E4E4E7",
    selectedText: "#141414",
    hover: "#F4F4F5",
  },
  dark: {
    primary: "#F5F5F5",
    primaryHover: "#D4D4D4",
    primaryActive: "#FFFFFF",
    text: "#F5F5F5",
    textSecondary: "#A1A1AA",
    layout: "#090909",
    container: "#171717",
    border: "#3F3F46",
    selected: "#2A2A2A",
    selectedText: "#F5F5F5",
    hover: "#242424",
  },
};

export function getThemeConfig(
  mode: ThemeMode,
  reducedMotion: boolean,
): ThemeConfig {
  const colors = themePalette[mode];

  return {
    algorithm: mode === "dark" ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      colorPrimary: colors.primary,
      colorPrimaryHover: colors.primaryHover,
      colorPrimaryActive: colors.primaryActive,
      colorText: colors.text,
      colorTextSecondary: colors.textSecondary,
      colorTextDescription: colors.textSecondary,
      colorBgLayout: colors.layout,
      colorBgContainer: colors.container,
      colorBorder: colors.border,
      borderRadius: 8,
      fontSize: 14,
      controlHeight: 40,
      ...(reducedMotion ? { motion: false } : {}),
    },
    components: {
      Form: {
        colorError: mode === "light" ? "#B42318" : "#FF7875",
      },
      Input: {
        inputFontSize: 16,
      },
      Button: {
        primaryColor: mode === "light" ? "#FFFFFF" : "#111111",
      },
      Layout: {
        headerBg: colors.container,
        siderBg: colors.container,
      },
      Menu: {
        itemSelectedBg: colors.selected,
        itemSelectedColor: colors.selectedText,
        itemHoverBg: colors.hover,
      },
    },
  };
}
