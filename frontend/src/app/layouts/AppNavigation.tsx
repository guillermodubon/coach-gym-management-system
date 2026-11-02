import {
  ApartmentOutlined,
  HomeOutlined,
  ShopOutlined,
} from "@ant-design/icons";
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
  const selectedKey =
    location.pathname === "/"
      ? "/"
      : location.pathname === "/organizacion"
        ? "/organizacion"
        : location.pathname.startsWith("/sucursales")
          ? "/sucursales"
          : "";

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
          {
            key: "/organizacion",
            icon: <ApartmentOutlined aria-hidden="true" />,
            label: (
              <NavLink
                aria-label="Organización"
                onClick={onNavigate}
                to="/organizacion"
              >
                Organización
              </NavLink>
            ),
            title: "Organización",
          },
          {
            key: "/sucursales",
            icon: <ShopOutlined aria-hidden="true" />,
            label: (
              <NavLink
                aria-label="Sucursales"
                onClick={onNavigate}
                to="/sucursales"
              >
                Sucursales
              </NavLink>
            ),
            title: "Sucursales",
          },
        ]}
        mode="inline"
        selectedKeys={selectedKey ? [selectedKey] : []}
      />
    </nav>
  );
}
