import { ArrowLeftOutlined } from "@ant-design/icons";
import { Typography } from "antd";
import { Link } from "react-router";
import { IdentityCard, IdentityLayout } from "../../app/layouts/IdentityLayout";
import { PasswordChangeForm } from "./PasswordChangeForm";
import { useAuth } from "./useAuth";

export function VoluntaryPasswordChangePage() {
  const auth = useAuth();
  const pending = auth.pendingAction === "password";

  return (
    <IdentityLayout>
      <IdentityCard>
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Cambiar contraseña
        </Typography.Title>
        <Typography.Paragraph>
          Después de actualizarla, deberás iniciar sesión de nuevo con la
          contraseña nueva.
        </Typography.Paragraph>
        <PasswordChangeForm submitLabel="Cambiar contraseña" />
        <Link
          aria-disabled={pending}
          className="cg-password-back ant-btn ant-btn-default"
          onClick={(event) => {
            if (pending) event.preventDefault();
          }}
          tabIndex={pending ? -1 : undefined}
          to="/mi-cuenta"
        >
          <ArrowLeftOutlined aria-hidden="true" />
          <span>Volver a los ajustes</span>
        </Link>
      </IdentityCard>
    </IdentityLayout>
  );
}
