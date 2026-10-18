import { HomeOutlined } from "@ant-design/icons";
import { Menu } from "antd";
import { NavLink, useLocation } from "react-router";

interface AppNavigationProps {
  collapsed?: boolean;
  onNavigate?(): void;
}

export function AppNavigation({
  collapsed = false,
  onNavigate,
}: AppNavigationProps) {
  const location = useLocation();
  const selectedKey = location.pathname === "/" ? "/" : "";

  return (
    <nav aria-label="Navegación principal">
      <Menu
        inlineCollapsed={collapsed}
        items={[
          {
            key: "/",
            icon: <HomeOutlined aria-hidden="true" />,
            label: (
              <NavLink aria-label="Inicio" onClick={onNavigate} to="/">
                Inicio
              </NavLink>
            ),
            title: "Inicio",
          },
        ]}
        mode="inline"
        selectedKeys={selectedKey ? [selectedKey] : []}
      />
    </nav>
  );
}
