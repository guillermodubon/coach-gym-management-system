import { useEffect, useRef, useState } from "react";
import { LogoutOutlined } from "@ant-design/icons";
import { Avatar, Button, Dropdown } from "antd";
import type { MenuProps } from "antd";
import type { CurrentUser } from "../../shared/api/contracts";
import { getAccountDisplayName, getAuthorityLabel } from "./account-labels";
import { getAccountInitials } from "./initials";
import "./account-menu.css";

export interface AccountMenuProps {
  user: CurrentUser;
  onLogout(): void;
}

export function AccountMenu({ user, onLogout }: AccountMenuProps) {
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const name = getAccountDisplayName(user);
  const initials = getAccountInitials(user);
  const authority = getAuthorityLabel(user) ?? "Autoridad no disponible";

  useEffect(() => {
    if (!open) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      setOpen(false);
      queueMicrotask(() => triggerRef.current?.focus());
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [open]);

  const menu: MenuProps = {
    "aria-label": "Cuenta",
    items: [
      {
        key: "account-summary",
        disabled: true,
        label: (
          <span className="cg-account-menu-summary">
            <strong>{name}</strong>
            <span>{authority}</span>
          </span>
        ),
      },
      { type: "divider" },
      {
        key: "logout",
        icon: <LogoutOutlined aria-hidden="true" />,
        label: "Cerrar sesión",
      },
    ],
    onClick: ({ key }) => {
      if (key === "logout") onLogout();
    },
  };

  return (
    <Dropdown
      classNames={{ root: "cg-account-popup" }}
      destroyOnHidden
      getPopupContainer={(trigger) => trigger.parentElement ?? document.body}
      menu={menu}
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen);
        if (!nextOpen) {
          queueMicrotask(() => triggerRef.current?.focus());
        }
      }}
      open={open}
      placement="bottomRight"
      trigger={["click"]}
    >
      <Button
        aria-expanded={open}
        aria-haspopup="menu"
        aria-label={`Menú de cuenta de ${name}`}
        className="cg-account-trigger"
        icon={
          <Avatar
            aria-hidden="true"
            className="cg-account-avatar"
            data-initials={initials}
            size={32}
          />
        }
        ref={triggerRef}
        type="text"
      >
        <span className="cg-account-trigger-name">{name}</span>
      </Button>
    </Dropdown>
  );
}
