import { Button, Typography } from "antd";
import { IdentityCard, IdentityLayout } from "../../app/layouts/IdentityLayout";
import { RequiredPasswordChangeForm } from "./RequiredPasswordChangeForm";
import { useAuth } from "./useAuth";

export function RequiredPasswordChangePage() {
  const auth = useAuth();
  const pending = auth.pendingAction !== null;

  return (
    <IdentityLayout>
      <IdentityCard>
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Actualiza tu contraseña
        </Typography.Title>
        <Typography.Paragraph>
          Debes cambiar tu contraseña antes de continuar.
        </Typography.Paragraph>
        <RequiredPasswordChangeForm />
        <Button
          className="cg-password-logout"
          disabled={pending}
          onClick={() => void auth.logout()}
        >
          Cerrar sesión
        </Button>
      </IdentityCard>
    </IdentityLayout>
  );
}
