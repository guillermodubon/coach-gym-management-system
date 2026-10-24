import { useEffect, useState } from "react";
import type { PropsWithChildren, ReactNode } from "react";
import {
  CloseOutlined,
  MenuOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from "@ant-design/icons";
import { Button, Drawer, Layout, Tooltip } from "antd";
import { Link, useLocation } from "react-router";
import { ThemeToggle } from "../../shared/theme/ThemeToggle";
import { AppNavigation } from "./AppNavigation";
import "./app-layout.css";

export interface AppLayoutProps extends PropsWithChildren {
  accountSlot?: ReactNode;
  contextSlot?: ReactNode;
  title?: string;
}

function useDesktopLayout(): boolean {
  const [isDesktop, setIsDesktop] = useState(() => {
    try {
      return window.matchMedia("(min-width: 992px)").matches;
    } catch {
      return true;
    }
  });

  useEffect(() => {
    let media: MediaQueryList;
    try {
      media = window.matchMedia("(min-width: 992px)");
    } catch {
      return;
    }

    const update = (event: MediaQueryListEvent) => setIsDesktop(event.matches);
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
  }, []);

  return isDesktop;
}

export function AppLayout({
  children,
  accountSlot,
  contextSlot,
  title = "Inicio",
}: AppLayoutProps) {
  const isDesktop = useDesktopLayout();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    if (isDesktop) setDrawerOpen(false);
  }, [isDesktop]);

  useEffect(() => {
    setDrawerOpen(false);
  }, [location.pathname]);

  return (
    <Layout className="cg-app-layout">
      <a className="cg-skip-link" href="#main-content">
        Saltar al contenido
      </a>
      <Layout.Sider
        aria-label="Barra lateral"
        className="cg-sidebar"
        collapsed={collapsed}
        collapsedWidth={80}
        theme="light"
        trigger={null}
        width={240}
      >
        <div className="cg-sidebar-brand">
          <Link to="/">
            <span aria-hidden="true" className="cg-brand-mark">
              CG
            </span>
            {!collapsed ? <span>Coach Gym</span> : null}
          </Link>
          <Tooltip
            getPopupContainer={(trigger) =>
              trigger.parentElement ?? document.body
            }
            title={collapsed ? "Expandir menú" : "Contraer menú"}
          >
            <Button
              aria-label={collapsed ? "Expandir menú" : "Contraer menú"}
              className="cg-sidebar-collapse cg-icon-button"
              icon={
                collapsed ? (
                  <MenuUnfoldOutlined aria-hidden="true" />
                ) : (
                  <MenuFoldOutlined aria-hidden="true" />
                )
              }
              onClick={() => setCollapsed((current) => !current)}
              type="text"
            />
          </Tooltip>
        </div>
        <AppNavigation collapsed={collapsed} />
      </Layout.Sider>

      <Layout className="cg-content-layout">
        <header className="cg-header">
          <div className="cg-header-leading">
            <Button
              aria-controls="mobile-navigation"
              aria-expanded={drawerOpen}
              aria-label="Abrir navegación"
              className="cg-mobile-nav-trigger cg-icon-button"
              icon={<MenuOutlined aria-hidden="true" />}
              onClick={() => setDrawerOpen(true)}
              type="text"
            />
            <strong>{title}</strong>
          </div>
          <div className="cg-header-actions">
            {contextSlot ? (
              <div className="cg-header-context">{contextSlot}</div>
            ) : null}
            <div className="cg-header-controls">
              <ThemeToggle />
              {accountSlot ? (
                <div className="cg-header-account">{accountSlot}</div>
              ) : null}
            </div>
          </div>
        </header>

        <Layout.Content
          aria-labelledby="main-heading"
          className="cg-content cg-main-content"
          id="main-content"
          tabIndex={-1}
        >
          {children}
        </Layout.Content>
      </Layout>

      <Drawer
        className="cg-mobile-drawer"
        closable={{
          closeIcon: <CloseOutlined aria-hidden="true" />,
          "aria-label": "Cerrar navegación",
        }}
        focusable={{ focusTriggerAfterClose: true, trap: true }}
        getContainer={false}
        id="mobile-navigation"
        mask={{ closable: true }}
        onClose={() => setDrawerOpen(false)}
        open={drawerOpen}
        placement="left"
        size="min(320px, calc(100vw - 24px))"
        title="Navegación"
      >
        <AppNavigation onNavigate={() => setDrawerOpen(false)} />
      </Drawer>
    </Layout>
  );
}
