import { Typography } from "antd";
import { IdentityCard, IdentityLayout } from "../../app/layouts/IdentityLayout";
import { LoginForm } from "./LoginForm";

export function LoginPage() {
  return (
    <IdentityLayout>
      <IdentityCard>
        <Typography.Title id="main-heading" level={1} tabIndex={-1}>
          Iniciar sesión
        </Typography.Title>
        <LoginForm />
      </IdentityCard>
    </IdentityLayout>
  );
}
