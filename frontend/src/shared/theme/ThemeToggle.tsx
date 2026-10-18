import { MoonOutlined, SunOutlined } from "@ant-design/icons";
import { Button } from "antd";
import { useTheme } from "./useTheme";

export function ThemeToggle() {
  const { mode, toggleTheme } = useTheme();
  const nextMode = mode === "light" ? "dark" : "light";
  const label =
    nextMode === "dark" ? "Activar tema oscuro" : "Activar tema claro";

  return (
    <Button
      aria-label={label}
      className="cg-icon-button"
      icon={
        mode === "light" ? (
          <MoonOutlined aria-hidden="true" />
        ) : (
          <SunOutlined aria-hidden="true" />
        )
      }
      onClick={toggleTheme}
      type="text"
    />
  );
}
