import {
  BgColorsOutlined,
  SafetyOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { Card, Typography } from "antd";
import { Link } from "react-router";
import { ThemeToggle } from "../../shared/theme/ThemeToggle";
import "./account-settings.css";

export function AccountSettingsPage() {
  return (
    <div className="cg-account-settings">
      <div>
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Ajustes de mi cuenta
        </Typography.Title>
        <Typography.Paragraph>
          Administra tu perfil, apariencia y seguridad personal.
        </Typography.Paragraph>
      </div>

      <div className="cg-account-settings-grid">
        <Card className="cg-account-setting-card" variant="outlined">
          <UserOutlined
            aria-hidden="true"
            className="cg-account-setting-icon"
          />
          <Typography.Title level={2}>Perfil y foto</Typography.Title>
          <Typography.Paragraph>
            Actualiza el nombre que aparece en tu cuenta y tu foto de perfil.
          </Typography.Paragraph>
          <Link
            className="cg-account-setting-link ant-btn ant-btn-default"
            to="/mi-perfil"
          >
            Abrir mi perfil
          </Link>
        </Card>

        <Card className="cg-account-setting-card" variant="outlined">
          <BgColorsOutlined
            aria-hidden="true"
            className="cg-account-setting-icon"
          />
          <Typography.Title level={2}>Apariencia</Typography.Title>
          <Typography.Paragraph>
            Elige el tema visual de esta aplicación. La preferencia se guarda en
            este dispositivo.
          </Typography.Paragraph>
          <div className="cg-account-setting-theme">
            <span>Tema claro u oscuro</span>
            <ThemeToggle />
          </div>
        </Card>

        <Card className="cg-account-setting-card" variant="outlined">
          <SafetyOutlined
            aria-hidden="true"
            className="cg-account-setting-icon"
          />
          <Typography.Title level={2}>Seguridad</Typography.Title>
          <Typography.Paragraph>
            Cambia tu contraseña. Al guardarla, deberás iniciar sesión de nuevo.
          </Typography.Paragraph>
          <Link
            className="cg-account-setting-link ant-btn ant-btn-primary"
            to="/mi-cuenta/contrasena"
          >
            Cambiar contraseña
          </Link>
        </Card>
      </div>
    </div>
  );
}
